from dataclasses import dataclass

from app.agents.models import SkillDefinition
from app.schemas.runtime import RuntimeRunRequest


@dataclass(frozen=True)
class AgentExecutionContext:
    tenant_id: str
    user_id: str
    scene_code: str
    data_scope: str
    permissions: tuple[str, ...]
    business_type: str | None
    business_id: str | None
    credential_key: str | None
    trace_id: str | None
    skills: tuple[SkillDefinition, ...]
    run_id: str = ""
    conversation_id: str | None = None

    @classmethod
    def from_request(
            cls,
            request: RuntimeRunRequest,
            skills: list[SkillDefinition]) -> "AgentExecutionContext":
        permissions = request.context.get("permissions") or []
        if not isinstance(permissions, list):
            permissions = []
        return cls(
            tenant_id=request.tenant_id,
            user_id=request.user_id,
            run_id=str(request.run_id or ""),
            conversation_id=str(request.conversation_id) if request.conversation_id else None,
            scene_code=(request.scene_code or "GENERAL_ASSISTANT").strip().upper(),
            data_scope=str(request.context.get("dataScope") or "SELF"),
            permissions=tuple(str(item) for item in permissions if item),
            business_type=request.business_type,
            business_id=request.business_id,
            credential_key=request.run_id if request.authorization else None,
            trace_id=str(request.context.get("traceId") or "") or None,
            skills=tuple(skills),
        )

    def has_any_authority(self, *authorities: str) -> bool:
        current = set(self.permissions)
        if "*" in current:
            return True
        return any(authority in current for authority in authorities)

    def find_skill(self, code: str) -> SkillDefinition | None:
        normalized = (code or "").strip().lower()
        for skill in self.skills:
            values = [skill.code, skill.name, skill.id]
            if any((value or "").strip().lower() == normalized for value in values):
                return skill
        return None
