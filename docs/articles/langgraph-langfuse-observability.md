# LangGraph 智能体上线后，我是怎么用 Langfuse 把可观测做实的

我们组的 CRM 项目里有一个独立出去的 AI 运行时服务 `crm-ai-runtime`，Python + FastAPI + LangGraph，负责线索分析、客户深度总结、营销助手这些场景的智能体编排。Java 那边只管业务数据、权限和配置，Agent 执行全在这边。

服务跑起来之后我很快撞上一个问题：**智能体是个黑盒**。运营反馈"这次线索分析结果不太对"，我手里只有最终输出的 JSON 和一堆 uvicorn 日志。中间是哪个节点检索的、检索到了什么、模型第几轮调了什么工具、token 花了多少，一概不知。排一次问题靠在代码里临时加 print，排完删掉，下次再来。

这篇文章记录我把可观测彻底做实的完整过程：为什么选 Langfuse、怎么部署、代码怎么接、元数据怎么设计、以及踩过的几个坑。代码都来自真实仓库，不是示意代码。

## 一、先交代一下被观测的对象长什么样

先看被观测的业务代码，后面所有接入点都要对号入座。

线索分析是典型的 LangGraph 状态图，七个节点：读上下文 → 条件分支决定要不要联网检索客户公开信息 → 组装分析消息 → ReAct 智能体节点 → 落库 → 校验结构化结果 → 输出。核心在 `app/workflows/lead_analysis/workflow.py`：

```python
builder = StateGraph(LeadAnalysisState, context_schema=AgentExecutionContext)
builder.add_node("prepare_context", observable_node("prepare_context", "读取线索上下文")(
    lead_analysis_nodes.prepare_context))
builder.add_node("company_web_search", observable_node("company_web_search", "检索客户公开信息")(
    lead_analysis_nodes.company_web_search))
builder.add_node("analysis_agent", analysis_agent)
# ... 后面还有 record_analysis / validate_output / finalize_result
```

执行入口在 `app/runtime/executor.py`，整个 run 的生命周期是：补全 run_id → 构建运行上下文（加载 Agent 定义、Skills、MCP、工具）→ 起 run 记录 → `graph.astream` 流式执行四种 stream_mode → 汇总事件写库。线程 ID 用 `sceneCode:tenantId:userId:conversationId` 拼出来，正好接 LangGraph 的 Postgres checkpointer 做会话恢复。

```python
async for part in bundle.graph.astream(
        bundle.input_state,
        config=self._runtime_config(request, langsmith_extra),
        context=bundle.execution_context,
        stream_mode=["messages", "updates", "values", "custom"],
        version="v2"):
    await accumulator.consume(part)
```

也就是说，我要观测的东西有三层：

1. **一次完整 run**（对应一次用户请求，有租户、用户、场景、会话 ID）；
2. **图里的每个节点**（尤其联网检索这种 IO 节点，耗时不稳定）;
3. **智能体节点内部**的每一轮模型调用和工具调用（这里最容易烧钱，也最容易出错）。

## 二、为什么是 Langfuse，不是继续用 LangSmith

项目早期我接的是 LangSmith——毕竟 LangChain 全家桶，`@traceable` 装饰器一挂就能用，当时 `trace_utils.py` 里就是这么写的。真正用下来，有三个点让我决定换：

- **数据出网**。CRM 里跑的是真实客户数据、跟进记录、合同信息，trace 里全是这些。LangSmith 是纯 SaaS，数据在国外，安全评审过不去。
- **成本**。LangSmith 按 trace 和 token 计费，营销助手这种高频闲聊场景一天几千次 run，账单看着心疼。
- **留存**。SaaS 的 trace 留一段时间就没了，我想把分析 trace 和业务 run 记录长期关联起来做复盘。

Langfuse 是开源的（MIT），核心功能 trace / session / prompt / 评估全都有自托管版，一套 docker-compose 起在内部机房，数据不出门。它对 LangChain/LangGraph 的支持是官方一等公民，有现成的 CallbackHandler，不用自己造上报协议。

## 三、部署：十分钟的事

内网机器上拉官方 compose 文件直接起（v3 架构是两个服务 + Postgres + ClickHouse + Redis + MinIO）：

```bash
curl -fsSL https://raw.githubusercontent.com/langfuse/langfuse/main/docker-compose.yml -o docker-compose.yml
docker compose up -d
```

默认端口 3000，浏览器打开，注册第一个账号（自动成为 admin），建一个 Organization 和 Project，在项目设置里创建一对 API Key（public key + secret key）。就这么多，不需要再干别的。

生产环境建议单独给 ClickHouse 和 Postgres 挂盘，trace 量上来之后 ClickHouse 是主要存储。

## 四、接入：我没用"零代码"，选了显式 CallbackHandler

Langfuse 有两种接法。

**方式一：环境变量 drop-in。** 装好 `langfuse` 包后只设环境变量：

```bash
export LANGFUSE_PUBLIC_KEY=pk-lf-xxx
export LANGFUSE_SECRET_KEY=sk-lf-xxx
export LANGFUSE_HOST=http://langfuse.internal:3000
```

LangChain 检测到这几个变量会自动挂 tracer，业务代码一行不改。

听起来很美，但我没用它，两个原因：一是它和 LangSmith 的开关变量会打架，我的 `trace_enabled()` 判断的是 `LANGSMITH_TRACING`，迁移期两套并存容易互相触发或互相抑制；二是零代码意味着我控制不了**哪些请求要上报、上报时带什么业务身份**——对 To B 多租户系统来说，一条 trace 不知道属于哪个租户哪个场景，等于白采。

**方式二：显式构造 CallbackHandler，塞进 LangGraph 的 config。** 这是我最终采用的。先装依赖：

```bash
pip install "langfuse>=3.0.0"
```

改造点集中在 `executor.py` 的 `_runtime_config`，也就是构造 `astream` 那个 config dict 的地方：

```python
from langfuse.langchain import CallbackHandler

def _runtime_config(self, request, langsmith_extra):
    config = {
        "configurable": {"thread_id": self._thread_id(request)},
        "recursion_limit": ...,
    }
    if trace_enabled():
        config["callbacks"] = [CallbackHandler(
            trace_name=f"AI场景运行-{request.scene_code}",
            user_id=str(request.user_id) if request.user_id else None,
            session_id=str(request.conversation_id) if request.conversation_id else None,
            tags=["crm", "ai-runtime", request.scene_code],
            metadata=runtime_trace_metadata(request),
        )]
    return config
```

`session_id` 我填的是 `conversation_id` 而不是 run_id——这样同一通会话的多次 run 会自动归到 Langfuse 的同一个 Session 下，前端工作台里一个会话对应一条时间线，排查"这个客户聊了五轮为什么越来越偏"这种问题特别直观。

`runtime_trace_metadata` 是我原来为 LangSmith 写的函数（在 `trace_utils.py` 里），把请求里的租户、用户、场景、业务单号、agentCode、模型名都抽出来打成字典，Langfuse 直接复用，一行没改。这也是我先写脱敏层的好处：观测后端换牌子，业务语义层不用动。

做完这一步，重启服务，跑一次线索分析，Langfuse 界面上就能看到完整的 trace 树了：根节点是这次 run，下面按图结构展开，`analysis_agent` 节点里面每一轮 LLM 调用、每次工具调用的入参出参、耗时、token 用量，全部自动采到。**CallbackHandler 挂在 config 上，LangGraph 的节点和智能体内部不需要任何埋点**，这是选 LangChain 生态做观测最大的红利。

## 五、把图节点的业务语义补进 trace

自动采集拿到的节点名是 `prepare_context`、`company_web_search` 这种英文代号，开发和运维看得懂，看 trace 的人不一定。我之前写过一层 `observable_node` 装饰器（`app/workflows/node_observability.py`），用 `get_stream_writer()` 往 SSE 流里发 `GRAPH_NODE_STATUS` 事件，前端因此能显示"检索客户公开信息…完成"。现在顺手把这层信息也送进 Langfuse，两边的节点状态就对齐了：

```python
from langfuse import observe_client  # v3 的低开销手动上报

def observable_node(node_name, display_name):
    def decorator(func):
        @wraps(func)
        async def wrapper(state, runtime):
            started_at = time.perf_counter()
            # ... 原有 writer(...) 逻辑不动 ...
            try:
                result = await func(state, runtime)
                elapsed_ms = round((time.perf_counter() - started_at) * 1000, 3)
                observe_client.log_event(name=f"graph.node.{node_name}",
                                         metadata={"displayName": display_name,
                                                   "elapsedMs": elapsed_ms,
                                                   "status": "SUCCESS"})
                return result
            except Exception:
                observe_client.log_event(name=f"graph.node.{node_name}",
                                         metadata={"status": "FAILED"})
                raise
        return wrapper
    return decorator
```

这一步不是必须的，但做完之后有个实际收益：Langfuse 的 dashboard 里可以按事件名统计各节点成功率和耗时分布，"联网检索节点 P95 超过 8 秒"这类结论可以直接拿数据说话，而不是靠翻日志估。

## 六、脱敏和采样：上生产前必须想清楚的两件事

**脱敏要放在上报前，不能指望观测平台。** 我的项目里有个配置开关 `trace_capture_payload`，默认 `false`——也就是说默认只上报元信息（消息长度、上下文的 key 列表、事件数、输出长度），**不报原始消息内容**；排障需要时按环境单独打开，并且所有进 trace 的 dict 都过一遍 `clean_for_trace`，把 `authorization`、`apiKey`、`token` 这类 key 递归替换成"已脱敏"，长文本截断到 2000 字符。这个策略在 LangSmith 时期就定了，切 Langfuse 原样保留。自托管不代表可以裸奔，数据库被拖走的场景永远要假设。

**按场景控量。** 营销助手这种高频场景全量上报，ClickHouse 也会胖得很快。我的做法是让 `trace_enabled()` 之外再支持按 sceneCode 的白名单（比如只对 `LEAD_ANALYZE`、`CUSTOMER_DEEP_SUMMARY` 这些重场景默认开启），闲聊场景按 10% 采样。控制在 `_runtime_config` 里加一个判断就行，成本立竿见影。

## 七、两个真实的排查案例

接完之后，之前两类"经典难题"变得很平常。

**案例一：线索分析偶发超时。** 运营反馈部分线索分析要跑 40 秒以上。打开 Langfuse 按 trace 排耗时，一眼看到瓶颈不在模型，而在 `company_web_search` 节点——SearXNG 检索详情页是串行抓的，命中慢站点时整条链路被拖住。修复方式很直接：详情页并发抓 + 单页超时。没有 Langfuse 之前，这个结论要靠在三个节点里加耗时日志、跑几十次、人肉汇总才能得出。

**案例二：智能体空转烧 token。** `analysis_agent` 的 trace 里能看到完整的多轮工具调用序列。有一次发现某类线索会让智能体连续调用知识库检索五六轮，每轮 query 几乎一样，最后靠 `ModelCallLimitExceededError` 兜底结束——这就是提示词没约束好"检索不到就基于已有信息给结论"。在系统提示词里补了两句之后，这类 run 的平均模型调用轮次从 7 轮降到 3 轮，单次 token 成本降了一半多。这种"改了提示词到底有没有用"的量化对比，看 Langfuse 的 usage 面板就够了。

## 八、踩过的坑

1. **v2 和 v3 的 API 不通用。** 网上很多教程还在写 `langfuse.authorize` / `CallbackHandler(session_id=...)` 传参的 v2 老写法，v3（2025 年起的主版本）初始化方式变了。装包时锁 `langfuse>=3.0.0`，照官方 Python SDK v3 文档写，别抄老博客。
2. **CallbackHandler 要挂在 config 上，不是全局。** 我一开始图省事设了环境变量想全局挂，结果和迁移期还开着的 LangSmith tracer 打架，出现一条 run 两边都报、字段错乱的情况。显式挂 config 之后互不干扰，LangSmith 老链路可以留着对照跑一段时间再关。
3. **异步上下文里注意 handler 生命周期。** FastAPI + `astream` 都是异步，handler 实例随 config 创建、随请求结束，不要做成模块级单例复用，否则并发的两条 run 会把事件串到同一条 trace 上。
4. **metadata 别塞大对象。** 我一开始把整个 `lead` 字典塞进 metadata，单条 trace 上百 KB，界面渲染卡。后来改成只放长度、数量、key 列表这类"形状信息"，需要原文时再开 `trace_capture_payload`。

## 九、还差什么

目前这条链路覆盖了 trace、session、usage 成本和节点事件。下一步我计划做两件事：一是把 Langfuse 的 **score** 接进来——线索分析产出的意向等级 30 天后转化为商机的真实结果回填成 trace 评分，就有了"AI 判断 vs 业务事实"的闭环数据；二是把 Java 侧的 run 记录（`agent_run` 表）和 Langfuse 的 trace ID 双向关联，业务侧工单和观测侧 trace 一键互跳。做完这两件，可观测就不只是"排障工具"，而是智能体迭代的度量基础设施。

---

一句话总结：**LangGraph 的可观测，接入成本几乎全在"想清楚上报什么"，而不是写代码**。CallbackHandler 挂上 config 之后，树形 trace、token 成本、工具调用明细都是白拿的；真正值钱的是你围绕租户、场景、会话设计的那层元数据，和上报前的脱敏纪律。这两样做好了，Langfuse 自托管一周就能让智能体从黑盒变成玻璃盒。
