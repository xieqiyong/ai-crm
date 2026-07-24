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

外部前端不直接访问本服务。
