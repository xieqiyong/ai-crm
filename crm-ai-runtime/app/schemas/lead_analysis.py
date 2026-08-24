from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class LeadConvertDraft(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    customer_name: str = Field(default="", alias="customerName", description="建议创建的客户名称")
    industry: str = Field(default="", description="能够从真实资料确认的行业")
    contact_name: str = Field(default="", alias="contactName", description="联系人姓名")
    contact_phone: str = Field(default="", alias="contactPhone", description="联系人电话")
    contact_email: str = Field(default="", alias="contactEmail", description="联系人邮箱")
    level: Literal["NORMAL", "IMPORTANT", "STRATEGIC"] = Field(default="NORMAL", description="客户级别")
    status: Literal[
        "POTENTIAL", "ACTIVE", "DEALING", "COOPERATED", "SLEEPING", "CHURNED", "BLACKLIST"
    ] = Field(
        default="POTENTIAL",
        description="建议客户状态",
    )
    remark: str = Field(default="", description="基于真实资料形成的转化备注")


class LeadCustomerProfile(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    company_scale: str = Field(default="", alias="companyScale", description="公开资料确认的公司规模")
    industry: str = Field(default="", description="公开资料确认的公司行业")
    source_urls: list[str] = Field(
        default_factory=list,
        alias="sourceUrls",
        max_length=3,
        description="支持公司档案结论的公开来源链接",
    )


class LeadAnalysisResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    conclusion_title: str = Field(alias="conclusionTitle", description="销售一眼能看懂的一句话结论")
    sales_conclusion: str = Field(alias="salesConclusion", description="基于证据的销售结论")
    stage: Literal[
        "NEW", "CONTACTED", "FOLLOWING", "QUALIFIED", "NURTURING",
        "CONVERTED", "INVALID", "DUPLICATE", "CLOSED", "UNKNOWN",
    ]
    priority: Literal["HIGH", "MEDIUM", "LOW"]
    recommend_convert: bool = Field(alias="recommendConvert", description="当前是否建议转化为客户")
    score: int = Field(ge=0, le=100, description="线索价值评分")
    confidence: float = Field(ge=0.0, le=1.0, description="结论置信度")
    key_findings: list[str] = Field(alias="keyFindings", max_length=4, description="最多四条关键证据")
    risk_warnings: list[str] = Field(alias="riskWarnings", max_length=4, description="最多四条风险提醒")
    next_actions: list[str] = Field(alias="nextActions", max_length=4, description="最多四条下一步动作")
    reason: str = Field(description="评分、优先级和转化建议的依据")
    next_action: str = Field(alias="nextAction", description="当前最推荐执行的一步")
    convert_draft: LeadConvertDraft = Field(alias="convertDraft")
    customer_profile: LeadCustomerProfile = Field(alias="customerProfile")
