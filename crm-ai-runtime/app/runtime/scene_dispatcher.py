from app.agents.conversation import conversation_agent_factory
from app.agents.config import workflow_code
from app.runtime.context import RuntimeContext
from app.schemas.runtime import RuntimeRunRequest
from app.workflows.lead_analysis.workflow import lead_analysis_workflow_factory


class SceneDispatcher:
    def __init__(self):
        self._workflow_factories = {
            "LEAD_ANALYSIS": lead_analysis_workflow_factory,
        }

    async def build(
            self,
            request: RuntimeRunRequest,
            runtime_context: RuntimeContext,
            checkpointer=None):
        selected_workflow = workflow_code(runtime_context.agent, request.scene_code)
        if selected_workflow == "STANDARD_AGENT":
            return await conversation_agent_factory.build(request, runtime_context, checkpointer)
        workflow_factory = self._workflow_factories.get(selected_workflow)
        if workflow_factory is not None:
            return await workflow_factory.build(request, runtime_context, checkpointer)
        raise RuntimeError("智能体配置的工作流不存在：%s" % selected_workflow)


scene_dispatcher = SceneDispatcher()
