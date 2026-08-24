from app.agents.factory import tool_calling_agent_factory
from app.runtime.context import RuntimeContext
from app.schemas.runtime import RuntimeRunRequest


class ConversationAgentFactory:
    async def build(
            self,
            request: RuntimeRunRequest,
            runtime_context: RuntimeContext,
            checkpointer=None):
        return await tool_calling_agent_factory.build(
            request,
            runtime_context,
            checkpointer,
        )


conversation_agent_factory = ConversationAgentFactory()
