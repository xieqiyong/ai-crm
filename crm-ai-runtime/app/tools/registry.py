from dataclasses import dataclass, field
from typing import Any


@dataclass
class ToolDefinition:
    name: str
    description: str
    scene_codes: set[str] = field(default_factory=set)
    metadata: dict[str, Any] = field(default_factory=dict)


class ToolRegistry:
    def __init__(self):
        self._tools: dict[str, ToolDefinition] = {}
        self.register(
            ToolDefinition(
                name="customer_web_search",
                description="检索客户公开信息并整理客户档案",
                scene_codes={"LEAD_ANALYZE", "CUSTOMER_DEEP_SUMMARY", "CHANNEL_ANALYZE"},
            )
        )
        self.register(
            ToolDefinition(
                name="knowledge_hybrid_search",
                description="检索公司知识库并返回可引用摘要",
                scene_codes={"LEAD_ANALYZE", "CUSTOMER_DEEP_SUMMARY", "MARKETING_ASSISTANT", "CHANNEL_ANALYZE"},
            )
        )
        self.register(
            ToolDefinition(
                name="crm_business_query",
                description="查询CRM线索、客户、商机和跟进记录",
                scene_codes={"MARKETING_ASSISTANT", "CUSTOMER_DEEP_SUMMARY", "LEAD_ANALYZE"},
            )
        )

    def register(self, value: ToolDefinition) -> None:
        self._tools[value.name] = value

    def resolve(self, scene_code: str | None) -> list[ToolDefinition]:
        normalized = (scene_code or "").strip().upper()
        return [
            item for item in self._tools.values()
            if not item.scene_codes or normalized in item.scene_codes
        ]


tool_registry = ToolRegistry()
