from typing import Annotated, Any, TypedDict

from langchain.messages import AnyMessage
from langgraph.graph.message import add_messages

from app.schemas.lead_analysis import LeadAnalysisResult


class LeadAnalysisState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    lead: dict[str, Any]
    customer_profile: dict[str, Any]
    structured_response: LeadAnalysisResult
    analysis_started_at_ns: int
    analysis_elapsed_ms: float
