import logging
from dataclasses import dataclass, field

from langchain_core.tools import BaseTool

from app.agents.models import AgentDefinition
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

logger = logging.getLogger(__name__)


@dataclass
class ToolDefinition:
    tool: BaseTool
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
            )
        )
        self.register(
            ToolDefinition(
                tool=knowledge_hybrid_search,
                authorities={"crm:assistant:use", "crm:knowledge:manage"},
            )
        )
        self._register_crm_tools()

    def register(self, value: ToolDefinition) -> None:
        self._tools[value.name] = value

    def resolve(
            self,
            request: RuntimeRunRequest,
            agent: AgentDefinition | None = None) -> list[ToolDefinition]:
        permissions = set(str(item) for item in (request.context.get("permissions") or []) if item)
        authorized = [
            item for item in self._tools.values()
            if self._authorized(item, permissions)
        ]
        configured_names = self._configured_names(agent)
        if configured_names is None or "*" in configured_names:
            return authorized
        missing = configured_names.difference(self._tools.keys())
        if missing:
            logger.warning("智能体配置了不存在的内置工具 agentId=%s tools=%s", agent.id if agent else "", sorted(missing))
        return [item for item in authorized if item.name in configured_names]

    def _register_crm_tools(self) -> None:
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
            self.register(ToolDefinition(tool=tool, authorities=authorities))

    def _configured_names(self, agent: AgentDefinition | None) -> set[str] | None:
        if agent is None:
            return None
        config = agent.config
        if "builtinTools" not in config and "builtin_tools" not in config:
            return None
        values = config.get("builtinTools")
        if values is None:
            values = config.get("builtin_tools")
        if isinstance(values, str):
            values = [item.strip() for item in values.split(",")]
        if not isinstance(values, list):
            return set()
        return {str(item).strip() for item in values if str(item).strip()}

    def _authorized(self, definition: ToolDefinition, permissions: set[str]) -> bool:
        if not definition.authorities or "*" in permissions:
            return True
        return bool(definition.authorities.intersection(permissions))


tool_registry = ToolRegistry()
