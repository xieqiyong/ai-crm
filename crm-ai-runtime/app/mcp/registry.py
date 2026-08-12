from app.agents.models import AgentDefinition, McpServerDefinition
from app.agents.repository import agent_repository
from app.schemas.runtime import RuntimeRunRequest


class McpRegistry:
    async def resolve(self, request: RuntimeRunRequest, agent: AgentDefinition) -> list[McpServerDefinition]:
        return await agent_repository.resolve_mcps(request, agent)


mcp_registry = McpRegistry()
