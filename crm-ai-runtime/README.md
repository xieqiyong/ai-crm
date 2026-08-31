# CRM AI Runtime

智能营销管理系统的独立 AI 运行时。Gateway 将 AI 请求直接路由到 FastAPI；智能体、会话、消息、运行记录、事件、Token 统计、Skills、MCP 和 LangGraph Checkpoint 均由 Python 侧负责。

当前实现采用 LangChain 1.3 与 LangGraph 1.2 的官方智能体范式：

- 使用 `create_agent` 构建基于 LangGraph 的标准模型—工具循环。
- 使用 `@tool` 和 `ToolRuntime` 定义工具，模型只能看到业务参数，租户、用户、权限和凭证不会暴露到工具参数 Schema。
- 使用 `ToolStrategy(PydanticModel)` 约束线索分析结构化结果，不再解析模型随意输出的 JSON 文本。
- 使用 `MultiServerMCPClient` 动态挂载 Streamable HTTP、SSE 和 Stdio MCP 服务。
- 使用 Skill 目录加 `load_skill` 实现渐进式披露，未使用的 Skill 不会整体塞入提示词。
- 使用 LangGraph v2 `messages / updates / custom / values` 多模式流，直接输出模型 Token、工具状态和最终状态。
- 使用官方 Checkpointer 保存会话图状态；生产环境通过异步 PostgreSQL 连接池复用连接。
- 通用助手使用 `create_agent`；线索分析保留显式 `StateGraph`，并把标准结构化 Agent 作为分析子图。
- 使用 `generate_report` 通用工具生成 Word、PDF 和 HTML，并通过受权限保护的下载接口返回文件卡片。

详细设计见 [docs/architecture.md](docs/architecture.md)。

## 请求链路

```text
浏览器 -> crm-gateway -> crm-ai-runtime
                           |
                           +-> 加载场景智能体、Skill、MCP、权限工具
                           +-> create_agent：model <-> tools
                           +-> Checkpoint：会话状态
                           +-> agent_run / agent_events / conversation：业务记录和Token统计
```

CRM 查询工具通过 Gateway 调用真实 CRM POST 接口，并透传当前登录用户的 JWT。因此租户、菜单权限和数据权限仍由 CRM 后端校验；工具不直接伪造或绕过业务数据。

## 目录职责

```text
app/
├─ agents/                    # 开放式对话智能体
│  ├─ conversation.py        # 客服、营销助手等对话 Agent 入口
│  ├─ factory.py             # create_agent、工具、Skill、MCP、结构化输出装配
│  └─ middleware.py          # 模型与工具调用可观测中间件
├─ workflows/                # 确定性业务编排
│  ├─ lead_analysis/         # 线索分析 Workflow
│  │  ├─ state.py            # 图状态定义
│  │  ├─ nodes.py            # 业务节点与条件路由
│  │  └─ workflow.py         # StateGraph 拓扑及 Agent 子图装配
│  └─ node_observability.py  # Workflow 节点统一计时
├─ runtime/                  # 运行时基础设施
│  ├─ scene_dispatcher.py    # sceneCode 到 Agent/Workflow 的注册与分发
│  ├─ executor.py            # Checkpoint、执行、取消、持久化
│  └─ stream_adapter.py      # LangGraph 流到前端 SSE 协议的适配
├─ tools/                    # 标准 LangChain 工具及权限注册
├─ reports/                  # Markdown报告解析、文件渲染和MinIO存储
├─ mcp/                      # MCP 动态加载
├─ persistence/              # Checkpoint 与业务持久化
└─ api/                      # FastAPI 接口
```

目录不是按接口拆分，而是按执行模型拆分：

- 客服助手、营销助手属于开放式问题求解，放在 `agents`，由模型在授权工具间自主选择。
- 线索分析、渠道分析等有明确阶段、分支、校验或人工确认的流程，放在 `workflows`。
- Workflow 可以把标准 Agent 当作一个节点，但不能自己重新实现 Tool Calling 循环。
- `runtime` 不包含场景业务，只负责统一执行协议和基础设施。

## 本地启动

Windows PowerShell：

```powershell
cd crm-ai-runtime
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

Linux：

```bash
cd crm-ai-runtime
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

本地工具调用通常配置：

```env
CRM_AI_CRM_API_BASE_URL=http://localhost:8090
CRM_AI_DATABASE_ENABLED=true
CRM_AI_DATABASE_URI=postgresql://用户名:密码@localhost:5432/crm
```

## 报告生成

智能体可以按需挂载 `generate_report` 工具。模型负责生成完整的 Markdown 报告内容，Python 使用确定性模板渲染文件，不让模型直接生成二进制内容。

支持格式：

- `docx`：适合销售继续修改。
- `pdf`：适合归档和对外发送。
- `html`：适合浏览器查看和二次转换。

本地调试可以将文件保存到本地目录：

```env
CRM_AI_REPORT_ENABLED=true
CRM_AI_REPORT_LOCAL_DIR=./data/reports
CRM_AI_REPORT_MINIO_ENABLED=false
```

生产环境建议存入 MinIO：

```env
CRM_AI_REPORT_MINIO_ENABLED=true
CRM_AI_REPORT_MINIO_ENDPOINT=http://crm-minio:9000
CRM_AI_REPORT_MINIO_ACCESS_KEY=访问账号
CRM_AI_REPORT_MINIO_SECRET_KEY=访问密码
CRM_AI_REPORT_MINIO_BUCKET=crm
```

报告元数据作为 `REPORT_READY` 事件写入 `agent_events`，文件下载时同时校验租户、当前用户、会话和报告归属。聊天历史会从事件中恢复报告卡片。

## Checkpoint

本地临时调试可以使用内存：

```env
CRM_AI_CHECKPOINT_ENABLED=true
CRM_AI_CHECKPOINT_BACKEND=memory
```

生产环境使用 PostgreSQL：

```env
CRM_AI_CHECKPOINT_ENABLED=true
CRM_AI_CHECKPOINT_BACKEND=postgres
CRM_AI_CHECKPOINT_POSTGRES_URI=postgresql://用户名:密码@数据库地址:5432/crm
CRM_AI_CHECKPOINT_POOL_MIN_SIZE=1
CRM_AI_CHECKPOINT_POOL_MAX_SIZE=10
```

根据项目数据库约束，应用启动不会自动创建或修改 Checkpoint 表。首次启用 PostgreSQL Checkpoint 时，由开发人员确认数据库后手动执行一次：

```bash
python -m app.persistence.setup_checkpoint
```

`conversationId` 会映射为隔离后的 LangGraph `thread_id`。同一会话可以续接状态；没有 `conversationId` 的任务会创建独立会话，避免不同线索、用户和租户之间串状态。

## 核心接口

- `POST /api/agent-assistant/run/stream`：智能体 SSE 流式会话。
- `POST /api/agent-assistant/run/stop`：终止正在执行的回答。
- `POST /api/agent-assistant/report/download`：下载当前用户会话中生成的报告文件。
- `POST /api/assistant/lead/analyze`：线索结构化 AI 分析。
- `POST /internal/ai/runtime/run`：受内部令牌保护的运行时接口。
- `POST /health`：健康检查。

## 测试

```bash
python -m compileall -q app tests
python -m unittest discover -s tests -v
python -m pip check
```

测试不调用真实大模型，使用可控的工具调用模型验证标准 ReAct 循环、运行时参数隐藏以及 Checkpoint 跨轮会话连续性。

## 可观测

默认日志记录模型耗时、工具耗时、Token 用量、场景和 Trace 编号，不记录工具参数值、模型密钥或 JWT。LangSmith 默认关闭；需要外部 Trace 时再配置 `LANGSMITH_TRACING=true`。未开启 LangSmith 也能通过本地结构化日志和 `agent_events` 查看运行过程。

## 官方参考

- LangChain Agents：<https://docs.langchain.com/oss/python/langchain/agents>
- LangChain Tools：<https://docs.langchain.com/oss/python/langchain/tools>
- LangChain MCP：<https://docs.langchain.com/oss/python/langchain/mcp>
- LangGraph Persistence：<https://docs.langchain.com/oss/python/langgraph/persistence>
- LangGraph Streaming：<https://docs.langchain.com/oss/python/langgraph/streaming>
