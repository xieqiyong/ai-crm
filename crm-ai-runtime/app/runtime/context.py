from dataclasses import dataclass
from typing import Any

from app.agents.models import AgentDefinition, McpServerDefinition, SkillDefinition
from app.agents.repository import agent_repository
from app.mcp.registry import mcp_registry
from app.memory.store import MemoryStore, memory_store
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
    memory: MemoryStore

    def to_state(self) -> dict[str, Any]:
        return {
            "agent": self.agent.to_runtime_agent().model_dump(by_alias=True, mode="json"),
            "skills": [item.to_runtime_resource().model_dump(by_alias=True, mode="json") for item in self.skills],
            "mcps": [item.to_runtime_resource().model_dump(by_alias=True, mode="json") for item in self.mcps],
            "tools": [
                {
                    "name": item.name,
                    "description": item.description,
                    "metadata": item.metadata,
                }
                for item in self.tools
            ],
        }


class RuntimeContextBuilder:
    async def build(self, request: RuntimeRunRequest) -> RuntimeContext:
        agent = await agent_repository.resolve_agent(request)
        skills = await skill_registry.resolve(request, agent)
        mcps = await mcp_registry.resolve(request, agent)
        tools = tool_registry.resolve(request.scene_code)
        request.agent = agent.to_runtime_agent()
        request.skills = [item.to_runtime_resource() for item in skills]
        request.mcps = [item.to_runtime_resource() for item in mcps]
        return RuntimeContext(
            request=request,
            agent=agent,
            skills=skills,
            mcps=mcps,
            tools=tools,
            memory=memory_store,
        )


runtime_context_builder = RuntimeContextBuilder()
