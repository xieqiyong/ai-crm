# crm-ai-runtime

智能营销管理系统独立 AI Runtime，Web 层使用 FastAPI，编排层使用 LangGraph。

当前第一阶段只实现 `LEAD_ANALYZE` 线索 AI 分析场景基础设施：

- FastAPI 内部接口
- LangGraph 场景注册
- 线索分析图
- OpenAI 兼容模型调用
- 客户公开信息搜索工具预留
- 运行事件返回

启动：

```bash
cd crm-ai-runtime
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

Java 后端只通过内部接口访问：

```text
POST /internal/ai/runtime/run
```

## 持久化

默认不启用 checkpoint。需要调试本地流程时可以先用内存：

```env
CRM_AI_CHECKPOINT_ENABLED=true
CRM_AI_CHECKPOINT_BACKEND=memory
```

生产环境建议使用 PostgreSQL：

```env
CRM_AI_CHECKPOINT_ENABLED=true
CRM_AI_CHECKPOINT_BACKEND=postgres
CRM_AI_CHECKPOINT_POSTGRES_URI=postgresql://app_user:please-change-db-password@crm-postgres:5432/crm
CRM_AI_CHECKPOINT_AUTO_SETUP=true
LANGGRAPH_STRICT_MSGPACK=true
```

`conversationId` 存在时会作为可续跑会话依据；没有 `conversationId` 时每次运行会生成独立 `runId`，避免同一条线索重复分析时混入上一次状态。

## Trace

Trace 使用 LangSmith。默认关闭：

```env
LANGSMITH_TRACING=false
LANGSMITH_API_KEY=
LANGSMITH_PROJECT=crm-ai-runtime-dev
LANGSMITH_ENDPOINT=https://api.smith.langchain.com
```

开启后：

```env
LANGSMITH_TRACING=true
LANGSMITH_API_KEY=你的 LangSmith Key
LANGSMITH_PROJECT=crm-ai-runtime-dev
```

默认只上报租户、用户、场景、业务编号、模型名称、token 和步骤摘要，不上报模型密钥。只有显式设置 `CRM_AI_TRACE_CAPTURE_PAYLOAD=true` 时，才会上报 prompt、线索内容和模型输出摘要。

外部前端不直接访问本服务。
