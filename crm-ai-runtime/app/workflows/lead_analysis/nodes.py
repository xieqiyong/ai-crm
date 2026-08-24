import json
from typing import Any, Literal

from langchain.messages import HumanMessage
from langgraph.runtime import Runtime

from app.runtime.execution_context import AgentExecutionContext
from app.schemas.lead_analysis import LeadAnalysisResult
from app.services.web_search import empty_profile
from app.tools.customer_web_search import customer_web_search
from app.workflows.lead_analysis.state import LeadAnalysisState


class LeadAnalysisNodes:
    async def prepare_context(
            self,
            state: LeadAnalysisState,
            runtime: Runtime[AgentExecutionContext]) -> dict[str, Any]:
        lead = state.get("lead") or {}
        if not lead.get("id"):
            raise ValueError("线索分析缺少真实线索上下文")
        return {
            "lead": lead,
            "customer_profile": state.get("customer_profile") or empty_profile(self.company_name(lead)),
        }

    def route_after_prepare(
            self,
            state: LeadAnalysisState) -> Literal["company_web_search", "analysis_agent"]:
        if self.company_name(state.get("lead") or {}):
            return "company_web_search"
        return "analysis_agent"

    async def company_web_search(
            self,
            state: LeadAnalysisState,
            runtime: Runtime[AgentExecutionContext]) -> dict[str, Any]:
        company_name = self.company_name(state.get("lead") or {})
        profile = await customer_web_search.search(company_name)
        return {
            "customer_profile": profile,
            "messages": [HumanMessage(content=(
                "线索分析Workflow已完成客户公开信息检索。以下仅为公开检索结果，无法确认的字段必须保持为空：\n"
                + json.dumps(profile, ensure_ascii=False, default=str)
            ))],
        }

    async def validate_output(
            self,
            state: LeadAnalysisState,
            runtime: Runtime[AgentExecutionContext]) -> dict[str, Any]:
        value = state.get("structured_response")
        if value is None:
            raise RuntimeError("线索分析智能体未生成结构化结果")
        result = value if isinstance(value, LeadAnalysisResult) else LeadAnalysisResult.model_validate(value)
        verified_urls = self.source_urls(state.get("customer_profile") or {})
        current_urls = [item for item in result.customer_profile.source_urls if item in verified_urls]
        result.customer_profile.source_urls = (current_urls or verified_urls)[:3]
        return {"structured_response": result}

    async def finalize_result(
            self,
            state: LeadAnalysisState,
            runtime: Runtime[AgentExecutionContext]) -> dict[str, Any]:
        value = state.get("structured_response")
        if value is None:
            raise RuntimeError("线索分析结果整理失败，结构化结果为空")
        return {"structured_response": value}

    def company_name(self, lead: dict[str, Any]) -> str:
        return str(lead.get("companyName") or "").strip()

    def source_urls(self, profile: dict[str, Any]) -> list[str]:
        values = profile.get("sourceUrls") or []
        if not isinstance(values, list):
            return []
        return [str(item).strip() for item in values if str(item).strip()][:3]


lead_analysis_nodes = LeadAnalysisNodes()
