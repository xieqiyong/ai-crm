# CRM AI Agent 标准架构设计

## 1. 设计目标

AI Runtime 不是一个把提示词转发给大模型的接口，而是独立的智能体执行平台。它负责场景隔离、模型调用、工具循环、结构化输出、动态 Skill、动态 MCP、会话状态、运行记录、Token 统计、取消执行和可观测。

Java CRM 继续负责客户、线索、商机、跟进、知识库等业务领域能力；Gateway 将 AI 接口直接路由到 Python。Python 工具通过受保护的 CRM API 获取真实数据，不在智能体代码中复制业务查询规则。

场景采用双轨编排：开放式通用助手使用 `create_agent`；具有固定业务阶段的线索分析使用显式 `StateGraph`，图中的智能分析节点仍然是官方 `create_agent` 子图。这样既保留 Workflow 的确定性和可观测性，也不重复实现模型—工具循环。

### 1.1 目录边界

```text
app/
├─ agents/                    开放式对话 Agent
│  ├─ conversation.py        客服/营销助手入口
│  ├─ factory.py             标准 create_agent 装配
│  └─ middleware.py          Agent 调用可观测
├─ workflows/                确定性业务 Workflow
│  ├─ lead_analysis/
│  │  ├─ state.py            State Schema
│  │  ├─ nodes.py            节点与路由条件
│  │  └─ workflow.py         StateGraph 拓扑
│  └─ node_observability.py  节点统一观测
├─ runtime/
│  ├─ scene_dispatcher.py    场景注册与分发
│  ├─ executor.py            图执行、Checkpoint和持久化
│  └─ stream_adapter.py      流事件协议转换
├─ tools/                    内置工具
├─ mcp/                      MCP动态工具
└─ persistence/              Checkpoint和业务记录
```

三个核心目录的职责不能互相侵入：

- `agents` 解决“目标明确但过程开放”的问题，例如客服问答、产品咨询、销售话术和跨业务查询。
- `workflows` 解决“过程必须受控”的问题，例如先读取线索、再检索企业、再分析、最后校验结构化结果。
- `runtime` 只负责选择执行单元并提供共用能力，不编写任何线索、客户或渠道业务步骤。

新增场景时，先判断它需要的是 Agent 还是 Workflow，再在 `SceneDispatcher` 注册。不能为了展示流程而把普通对话画成无意义的图，也不能把需要审计和确定顺序的业务流程全部塞进一个提示词。

## 2. 标准运行链路

```text
FastAPI 接收请求
  -> 校验 JWT、租户、用户和入口权限
  -> 按 sceneCode 加载数据库中的 Agent 配置
  -> 解析当前 Agent 绑定的 Skill 和 MCP
  -> 按用户权限筛选内置 CRM 工具
  -> ChatModelFactory 创建标准 BaseChatModel
  -> create_agent 编译 LangGraph
  -> model 节点判断回答或生成 tool_calls
  -> tools 节点并行执行工具并生成 ToolMessage
  -> 条件边返回 model 节点继续推理
  -> ToolStrategy 生成并校验结构化结果
  -> Checkpointer 保存图状态
  -> 运行记录、事件和 Token 用量写入业务表
  -> v2 Stream 转换为前端 SSE 事件
```

`create_agent` 生成的核心图只有 `model` 和 `tools` 两类节点。工具调用、ToolMessage 回填、循环退出和结构化输出重试由框架完成，不再自己解析 `tool_calls` 或拼装伪 ReAct 循环。

线索分析图为：

```text
START
  -> prepare_context
  -> company_web_search（有公司名称时）
  -> analysis_agent（标准Agent子图）
  -> validate_output
  -> finalize_result
  -> END
```

### 2.1 客服助手如何设计

客服助手使用标准 `create_agent`，它本身已经编译为 LangGraph，不需要再手写一套 `model -> tools -> model` 图：

```text
用户问题
  -> create_agent
      -> model 判断是否需要工具
      -> tools 执行知识库、客户、线索、跟进或MCP工具
      -> ToolMessage 回到 model
      -> model 基于证据生成答复
  -> Checkpoint 保存当前会话状态
  -> SSE 持续返回思考摘要、工具进度和回答增量
```

客服助手的能力边界由四层共同确定：

1. 场景智能体配置决定系统提示词、模型和最大模型调用次数。
2. 用户权限与场景共同决定可见的 CRM 工具。
3. 智能体绑定关系决定可加载的 Skill 与 MCP。
4. Checkpoint 的 `thread_id` 决定可续接的会话状态。

客服助手只负责回答和调用能力，不直接跨过 CRM API 修改数据库。具有风险的写操作必须封装成参数明确、权限受控、可审计的工具；需要审批时应升级为带 `interrupt` 的 Workflow。

### 2.2 业务 Workflow 如何设计

Workflow 先定义业务状态，再定义节点输入输出和条件边。节点分为三类：

- 确定性节点：读取上下文、数据校验、规则判断、结果持久化。
- 工具节点：调用固定的检索、转写或外部服务，不让模型决定是否跳过法定步骤。
- Agent 节点：在局部开放任务中使用 `create_agent`，例如结合知识库形成销售判断。

只把真正影响业务过程的阶段放入图。模型内部的每一次 Tool Call 不重复提升为前端流程节点，避免用户看到几十个“调用辅助能力”。

## 3. State 与 Checkpoint

### 3.1 State

标准 Agent State 的核心是消息列表。模型调用产生 `AIMessage`，工具调用产生 `ToolMessage`，LangGraph Reducer 负责追加消息。业务代码不把任意字典当作“记忆”手动传来传去。

### 3.2 Runtime Context

每次运行通过 `AgentExecutionContext` 注入以下不可持久化运行时信息：

- 租户编号和用户编号
- 数据权限范围和权限编码
- 业务对象类型和编号
- Trace 编号
- 已挂载 Skill 目录
- 临时凭证引用

工具通过 `ToolRuntime[AgentExecutionContext]` 读取这些信息。`runtime` 参数不会出现在模型看到的工具 Schema 中。

JWT 只保存在当前进程的短生命周期凭证仓库，Context 中仅保存不透明引用；运行结束立即删除，避免 JWT 进入 Checkpoint、Trace 或模型上下文。

### 3.3 Checkpoint

Checkpoint 保存 LangGraph 图状态，用于同一会话多轮续接、故障恢复和后续人工介入。业务表保存产品可见的数据：

| 存储 | 职责 |
| --- | --- |
| LangGraph Checkpoint 表 | 消息状态、节点状态、待执行任务、图版本状态 |
| `conversation` | 会话归属、标题、场景、业务对象和最后交互时间 |
| `agent_run` | 单次运行输入、输出、状态、耗时和 Token |
| `agent_events` | 工具执行和最终结果等可观测事件 |
| `agent_token_usage` | 用户每日 Token 聚合 |

两套存储职责不同，不能拿 `agent_events` 冒充 Checkpoint，也不应把 Checkpoint 直接作为前端会话列表。

生产使用 `AsyncPostgresSaver + AsyncConnectionPool`。应用不会在启动阶段自动执行 DDL；首次启用时手动运行 `python -m app.persistence.setup_checkpoint`。

## 4. Function Calling 与工具规范

内置工具使用 LangChain 官方 `@tool`：

- 函数签名决定 JSON Schema。
- Docstring 说明工具用途和调用边界。
- 返回真实 JSON 文本，模型基于工具结果形成答案。
- 工具异常交给标准 Agent 工具错误链路处理，不伪造空数据。
- 页容量等参数在工具层设置安全上限。
- 工具日志只记录工具名、参数名、耗时和状态，不记录客户明文或 JWT。

CRM 工具按 `sceneCode + permissions` 动态筛选。执行时继续把当前用户 JWT 传给 Gateway，由 Java 的 `@PreAuthorize`、租户和数据权限进行第二次校验。模型无法通过提示词获得未授权工具，也不能通过修改工具参数伪造租户。

当前内置工具包括：客户公开信息检索、知识库混合检索、线索查询、客户查询、跟进查询和商机查询。

## 5. 结构化输出

线索分析使用 Pydantic `LeadAnalysisResult` 和官方 `ToolStrategy`。字段类型、枚举、最大条数、评分范围和置信度范围均由 Schema 约束。

这与“提示词要求模型返回 JSON，然后用正则截取”有本质区别：

- 模型收到真实工具 Schema。
- 框架识别结构化工具调用。
- Pydantic 执行类型和范围校验。
- 校验失败由 Agent 循环反馈模型修正。
- 最终结果从 `structured_response` 获取。

因此不会再把半截 Function Call、Markdown 包裹 JSON 或普通文字误当成结构化结论。

## 6. Skills 渐进式披露

系统提示词只放 Skill 编码、名称和简短描述。模型判断当前任务需要某项 Skill 后，调用 `load_skill(skill_code)` 加载完整内容。

这种方式有三个好处：

- 未使用 Skill 不占用上下文 Token。
- 每个智能体只能加载数据库中实际绑定的 Skill。
- Skill 更新后下一次运行即可读取新版本，无需改图代码。

后续可在 Skill 表增加版本、内容 Hash、依赖工具和适用场景，实现版本锁定与灰度发布。

## 7. MCP 动态挂载

运行时从当前 Agent 的绑定关系读取 MCP 配置，再由 `MultiServerMCPClient` 加载工具。支持：

- Streamable HTTP
- SSE
- Stdio

不同 MCP 服务的工具名增加服务前缀，避免重名。生产默认 `fail fast`：已声明依赖的 MCP 不可用时，本次运行明确失败，不静默伪装成“没有数据”。

MCP 工具仍需由服务端落实租户和权限。对于无法安全注入用户上下文的第三方 MCP，不应挂载到包含 CRM 敏感数据的智能体。

## 8. 流式输出

运行时使用 LangGraph v2 多模式流：

- `messages`：模型 Token 和模型提供的推理摘要。
- `updates`：节点增量和 ToolMessage。
- `custom`：工具主动上报的业务进度。
- `values`：最新完整状态和最终结构化结果。

适配层只转换协议，不重新分词伪造“模型流”。如果模型或兼容接口不支持 Token 流，接口才对最终文本做兼容分片。

前端 SSE 保留 `ANSWER_DELTA`、`THOUGHT_DELTA`、`RUN_STATUS_CHANGED`、`ANSWER_FINISHED` 和 `RUN_FINISHED`，避免业务前端绑定 LangGraph 内部事件名称。

部分 OpenAI 兼容模型使用 `reasoning_content` 返回推理摘要。运行时在官方 `ChatOpenAI` 消息转换之上保留该扩展字段，再转换为 `THOUGHT_DELTA`；只展示模型供应商明确返回的摘要，不生成或伪造思考内容。

## 9. 可观测与 Token

`AgentMiddleware` 统一包裹模型和工具调用，记录：

- 场景、消息数和工具数
- 模型总耗时与 Token
- 工具名、参数名、耗时和成功状态
- 接口 Trace 编号和整图耗时

流式 `usage_metadata` 按消息编号去重聚合，再写入 `agent_run` 和每日 Token 表。取消运行标记为 `STOPPED`，不会把预估 Token 当成真实消耗。

LangSmith 是可选外部 Trace，不是运行依赖。默认关闭完整输入输出采集，避免客户数据离开内网。

智能体配置中的 `maxIters` 表示单次运行允许的大模型调用次数。运行时通过官方 `ModelCallLimitMiddleware` 精确计数；LangGraph `recursion_limit` 继续作为整图防死循环的第二层保护。两者不能混为同一个概念。

## 10. 多智能体演进边界

当前通用场景是标准工具型 Agent，线索分析是显式 Workflow 加标准 Agent 子图。需要多智能体时，在 LangGraph 上层增加 Supervisor Graph：每个专业 Agent 作为编译后的子图或受控工具，由 Supervisor 根据结构化路由结果转交；共享状态必须显式定义，Agent 之间不能直接共享全部上下文和权限。

不建议把“调用多个普通函数”命名为多智能体，也不建议在一个大提示词中模拟角色切换。只有当专业 Agent 拥有独立提示词、工具集合、状态边界和可观测运行时，才进入多智能体编排。

## 11. 已移除的非标准实现

- 在通用助手中手写 `StateGraph` 重复模拟 Agent 工具循环；确定性的业务 Workflow 仍然保留显式 `StateGraph`。
- 自己解析模型 Function Call 文本。
- 正则提取模型 JSON 作为主要结构化输出方式。
- 自定义字典 Memory 冒充 LangGraph Checkpoint。
- 手动拼接工具过程并在运行结束后重复回放。
- 把完整 Skill 一次性注入所有请求。

这些能力现在分别由 `create_agent`、`ToolStrategy`、`ToolRuntime`、官方 Checkpointer、v2 Streaming 和渐进式 Skill 加载承担。
