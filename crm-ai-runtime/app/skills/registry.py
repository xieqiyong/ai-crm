from app.agents.models import AgentDefinition, SkillDefinition
from app.agents.repository import agent_repository
from app.schemas.runtime import RuntimeRunRequest


class SkillRegistry:
    async def resolve(self, request: RuntimeRunRequest, agent: AgentDefinition) -> list[SkillDefinition]:
        return await agent_repository.resolve_skills(request, agent)

    def render_prompt(self, skills: list[SkillDefinition]) -> str:
        values = []
        for item in skills:
            if item.content:
                values.append(item.content)
        return "\n\n".join(values)


skill_registry = SkillRegistry()
