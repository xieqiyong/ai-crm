from dataclasses import dataclass
from app.agents.models import AgentDefinition, McpServerDefinition, SkillDefinition
from app.agents.repository import agent_repository
from app.mcp.registry import mcp_registry
from app.schemas.runtime import RuntimeRunRequest
from app.skills.registry import skill_registry
from app.tools.registry import ToolDefinition, tool_registry


@dataclass
class RuntimeContext:
    request: RuntimeRunRequest
    agent: AgentDefinition
    skills: list[SkillDefinition]
    mcps: list[McpServerDefinition]
    tools: list[ToolDefinition]


class RuntimeContextBuilder:
    async def build(self, request: RuntimeRunRequest) -> RuntimeContext:
        agent = await agent_repository.resolve_agent(request)
        skills = await skill_registry.resolve(request, agent)
        mcps = await mcp_registry.resolve(request, agent)
        tools = tool_registry.resolve(request)
        request.agent = agent.to_runtime_agent()
        request.skills = [item.to_runtime_resource() for item in skills]
        request.mcps = [item.to_runtime_resource() for item in mcps]
        return RuntimeContext(
            request=request,
            agent=agent,
            skills=skills,
            mcps=mcps,
            tools=tools,
        )


runtime_context_builder = RuntimeContextBuilder()
