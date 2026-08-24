from dataclasses import dataclass, field

from langchain_core.tools import BaseTool

from app.schemas.runtime import RuntimeRunRequest
from app.tools.builtin import (
    crm_customer_detail,
    crm_customer_page,
    crm_followup_detail,
    crm_followup_page,
    crm_lead_detail,
    crm_lead_page,
    crm_opportunity_detail,
    crm_opportunity_page,
    customer_web_search_tool,
    knowledge_hybrid_search,
)


@dataclass
class ToolDefinition:
    tool: BaseTool
    scene_codes: set[str] = field(default_factory=set)
    authorities: set[str] = field(default_factory=set)

    @property
    def name(self) -> str:
        return self.tool.name

    @property
    def description(self) -> str:
        return self.tool.description


class ToolRegistry:
    def __init__(self):
        self._tools: dict[str, ToolDefinition] = {}
        self.register(
            ToolDefinition(
                tool=customer_web_search_tool,
                scene_codes={"LEAD_ANALYZE", "CUSTOMER_DEEP_SUMMARY", "CHANNEL_ANALYZE"},
            )
        )
        self.register(
            ToolDefinition(
                tool=knowledge_hybrid_search,
                scene_codes={
                    "GENERAL_ASSISTANT", "LEAD_ANALYZE", "CUSTOMER_DEEP_SUMMARY",
                    "MARKETING_ASSISTANT", "CHANNEL_ANALYZE",
                },
                authorities={"crm:assistant:use", "crm:knowledge:manage"},
            )
        )
        self._register_crm_tools()

    def register(self, value: ToolDefinition) -> None:
        self._tools[value.name] = value

    def resolve(self, request: RuntimeRunRequest) -> list[ToolDefinition]:
        normalized = (request.scene_code or "").strip().upper()
        permissions = set(str(item) for item in (request.context.get("permissions") or []) if item)
        return [
            item for item in self._tools.values()
            if (not item.scene_codes or normalized in item.scene_codes)
            and self._authorized(item, permissions)
        ]

    def _register_crm_tools(self) -> None:
        scenes = {"GENERAL_ASSISTANT", "MARKETING_ASSISTANT", "CUSTOMER_DEEP_SUMMARY", "LEAD_ANALYZE"}
        values = [
            (crm_lead_page, {"crm:lead:view", "crm:lead:manage"}),
            (crm_lead_detail, {"crm:lead:view", "crm:lead:manage"}),
            (crm_customer_page, {"crm:customer:view", "crm:customer:manage"}),
            (crm_customer_detail, {"crm:customer:view", "crm:customer:manage"}),
            (crm_followup_page, {"crm:followup:view", "crm:followup:manage"}),
            (crm_followup_detail, {"crm:followup:view", "crm:followup:manage"}),
            (crm_opportunity_page, {"crm:opportunity:view", "crm:opportunity:manage"}),
            (crm_opportunity_detail, {"crm:opportunity:view", "crm:opportunity:manage"}),
        ]
        for tool, authorities in values:
            self.register(ToolDefinition(tool=tool, scene_codes=set(scenes), authorities=authorities))

    def _authorized(self, definition: ToolDefinition, permissions: set[str]) -> bool:
        if not definition.authorities or "*" in permissions:
            return True
        return bool(definition.authorities.intersection(permissions))


tool_registry = ToolRegistry()
