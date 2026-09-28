from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class MediaRefinementRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, coerce_numbers_to_str=True)

    followup_id: str = Field(alias="followupId")
    media_task_id: str = Field(alias="mediaTaskId")
    force: bool = False


class MediaProjectInfo(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    background: str = Field(default="", description="客户项目背景，只保留录音中明确表达的事实")
    requirements: str = Field(default="", description="项目核心需求，只保留录音中明确表达的事实")
    project_stage: Literal[
        "UNCONFIRMED", "DISCOVERY", "SOLUTION", "PREPARING", "APPROVED",
        "PROCUREMENT", "BIDDING", "NEGOTIATION", "IMPLEMENTING", "PAUSED",
    ] = Field(default="UNCONFIRMED", alias="projectStage")
    budget_status: Literal[
        "UNCONFIRMED", "NOT_APPLIED", "APPLYING", "APPROVED", "INSUFFICIENT", "NONE",
    ] = Field(default="UNCONFIRMED", alias="budgetStatus")
    tender_status: Literal[
        "UNCONFIRMED", "NOT_REQUIRED", "POSSIBLE", "REQUIRED", "IN_PROGRESS", "FINISHED",
    ] = Field(default="UNCONFIRMED", alias="tenderStatus")
    competition_status: Literal[
        "UNCONFIRMED", "NONE", "EXISTS",
    ] = Field(default="UNCONFIRMED", alias="competitionStatus")
    competitors: str = Field(default="", description="录音中明确提到的竞争对手，多个用顿号分隔")
    expected_purchase_date: str = Field(
        default="",
        alias="expectedPurchaseDate",
        description="录音中明确提到的预计采购日期，无法确认时留空",
    )


class MediaChunkInsight(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    summary: str = Field(description="当前片段的核心沟通结论")
    customer_needs: list[str] = Field(default_factory=list, alias="customerNeeds", max_length=8)
    key_facts: list[str] = Field(default_factory=list, alias="keyFacts", max_length=8)
    decisions: list[str] = Field(default_factory=list, max_length=6)
    risks: list[str] = Field(default_factory=list, max_length=6)
    objections: list[str] = Field(default_factory=list, max_length=6)
    next_actions: list[str] = Field(default_factory=list, alias="nextActions", max_length=6)
    uncertainties: list[str] = Field(default_factory=list, max_length=6)
    project_info: MediaProjectInfo = Field(default_factory=MediaProjectInfo, alias="projectInfo")


class MediaRefinementResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    summary: str = Field(description="本次沟通的销售摘要")
    customer_needs: list[str] = Field(default_factory=list, alias="customerNeeds", max_length=8)
    key_facts: list[str] = Field(default_factory=list, alias="keyFacts", max_length=8)
    decisions: list[str] = Field(default_factory=list, max_length=6)
    risks: list[str] = Field(default_factory=list, max_length=6)
    objections: list[str] = Field(default_factory=list, max_length=6)
    next_actions: list[str] = Field(default_factory=list, alias="nextActions", max_length=6)
    uncertainties: list[str] = Field(default_factory=list, max_length=6)
    project_info: MediaProjectInfo = Field(default_factory=MediaProjectInfo, alias="projectInfo")
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    source_segment_count: int = Field(default=1, alias="sourceSegmentCount", ge=1)

