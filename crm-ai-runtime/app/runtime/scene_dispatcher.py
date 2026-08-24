from app.agents.conversation import conversation_agent_factory
from app.runtime.context import RuntimeContext
from app.schemas.runtime import RuntimeRunRequest
from app.workflows.lead_analysis.workflow import lead_analysis_workflow_factory


class SceneDispatcher:
    def __init__(self):
        self._workflow_factories = {
            "LEAD_ANALYZE": lead_analysis_workflow_factory,
        }

    async def build(
            self,
            request: RuntimeRunRequest,
            runtime_context: RuntimeContext,
            checkpointer=None):
        scene_code = (request.scene_code or "").strip().upper()
        workflow_factory = self._workflow_factories.get(scene_code)
        if workflow_factory is not None:
            return await workflow_factory.build(request, runtime_context, checkpointer)
        return await conversation_agent_factory.build(request, runtime_context, checkpointer)


scene_dispatcher = SceneDispatcher()
