import json
import operator
from typing import Annotated, Any, Literal, TypedDict

from langgraph.graph import END, START, StateGraph

from app.schemas.runtime import RuntimeRunRequest
from app.services.event_bus import runtime_event
from app.services.llm import OpenAICompatibleClient, LlmResult, text
from app.services.web_search import empty_profile
from app.tools.customer_web_search import customer_web_search


class LeadAnalyzeState(TypedDict, total=False):
    request: dict[str, Any]
    runtime: dict[str, Any]
    lead: dict[str, Any]
    customer_profile: dict[str, Any]
    raw_output: str
    output: str
    usage: dict[str, int]
    events: Annotated[list[dict[str, Any]], operator.add]


llm_client = OpenAICompatibleClient()


def build_graph(checkpointer=None):
    builder = StateGraph(LeadAnalyzeState)
    builder.add_node("prepare_context", prepare_context)
    builder.add_node("company_web_search", company_web_search_node)
    builder.add_node("lead_analyze", lead_analyze_node)
    builder.add_node("validate_output", validate_output_node)
    builder.add_node("finalize", finalize_node)
    builder.add_edge(START, "prepare_context")
    builder.add_conditional_edges(
        "prepare_context",
        route_after_prepare,
        {
            "company_web_search": "company_web_search",
            "lead_analyze": "lead_analyze",
        },
    )
    builder.add_edge("company_web_search", "lead_analyze")
    builder.add_edge("lead_analyze", "validate_output")
    builder.add_edge("validate_output", "finalize")
    builder.add_edge("finalize", END)
    return builder.compile(checkpointer=checkpointer)


async def prepare_context(state: LeadAnalyzeState) -> dict[str, Any]:
    request = runtime_request(state)
    lead = resolve_lead(request)
    profile = empty_profile(resolve_company_name(lead))
    return {
        "lead": lead,
        "customer_profile": profile,
        "events": [runtime_event("STEP", metadata={"node": "prepare_context", "status": "SUCCESS"})],
    }


def route_after_prepare(state: LeadAnalyzeState) -> Literal["company_web_search", "lead_analyze"]:
    company_name = resolve_company_name(state.get("lead") or {})
    if company_name:
        return "company_web_search"
    return "lead_analyze"


async def company_web_search_node(state: LeadAnalyzeState) -> dict[str, Any]:
    company_name = resolve_company_name(state.get("lead") or {})
    profile = await customer_web_search.search(company_name)
    return {
        "customer_profile": profile,
        "events": [
            runtime_event(
                "TOOL",
                tool_name="customer_web_search",
                metadata={
                    "node": "company_web_search",
                    "status": "SUCCESS",
                    "available": profile.get("available", False),
                },
            )
        ],
    }


async def lead_analyze_node(state: LeadAnalyzeState) -> dict[str, Any]:
    request = runtime_request(state)
    system_prompt = build_system_prompt(request)
    user_prompt = build_user_prompt(request, state.get("lead") or {}, state.get("customer_profile") or {})
    llm_result = await llm_client.chat(request.agent, system_prompt, user_prompt)
    return {
        "raw_output": llm_result.content,
        "usage": usage_dict(llm_result),
        "events": [runtime_event("STEP", metadata={"node": "lead_analyze", "status": "SUCCESS"})],
    }


async def validate_output_node(state: LeadAnalyzeState) -> dict[str, Any]:
    lead = state.get("lead") or {}
    profile = state.get("customer_profile") or empty_profile(resolve_company_name(lead))
    parsed = parse_json_object(state.get("raw_output") or "")
    if parsed is None:
        parsed = fallback_result(lead, profile, state.get("raw_output") or "")
    else:
        parsed = normalize_result(lead, profile, parsed)
    output = json.dumps(parsed, ensure_ascii=False)
    return {
        "output": output,
        "events": [runtime_event("STEP", metadata={"node": "validate_output", "status": "SUCCESS"})],
    }


async def finalize_node(state: LeadAnalyzeState) -> dict[str, Any]:
    metadata = {"node": "finalize", "status": "SUCCESS"}
    metadata.update(state.get("usage") or {})
    return {
        "events": [
            runtime_event("FINAL_RESULT", content=state.get("output") or "", metadata=metadata),
        ],
    }


def runtime_request(state: LeadAnalyzeState) -> RuntimeRunRequest:
    return RuntimeRunRequest.model_validate(state.get("request") or {})


def resolve_lead(request: RuntimeRunRequest) -> dict[str, Any]:
    value = request.context.get("lead")
    if isinstance(value, dict):
        return value
    return {}


def build_system_prompt(request: RuntimeRunRequest) -> str:
    prompts = []
    if text(request.rendered_system_prompt):
        prompts.append(text(request.rendered_system_prompt))
    elif request.agent and text(request.agent.system_prompt):
        prompts.append(text(request.agent.system_prompt))
    skill_prompt = build_skill_prompt(request)
    if skill_prompt:
        prompts.append(skill_prompt)
    prompts.append(
        "你是线索分析智能体。只能基于输入的真实线索数据和工具返回结果分析，不能编造公司、联系人、电话、预算、沟通记录。"
    )
    prompts.append("最终只输出合法 JSON 对象，不输出 Markdown、代码块或自然语言前后缀。")
    prompts.append("customerProfile 只能填写工具结果或线索中能够确认的信息，不能猜测。")
    return "\n".join(prompts)


def build_skill_prompt(request: RuntimeRunRequest) -> str:
    values = []
    for item in request.skills:
        content = text(item.content)
        if content:
            values.append(content)
    return "\n\n".join(values)


def build_user_prompt(request: RuntimeRunRequest, lead: dict[str, Any], profile: dict[str, Any]) -> str:
    schema_text = {
        "conclusionTitle": "一句话标题",
        "salesConclusion": "销售可直接理解的结论",
        "stage": "NEW/FOLLOWING/QUALIFIED/CONVERTED/CLOSED/UNKNOWN",
        "priority": "HIGH/MEDIUM/LOW",
        "recommendConvert": False,
        "score": 0,
        "confidence": 0.0,
        "keyFindings": ["最多4条关键证据"],
        "riskWarnings": ["最多4条风险"],
        "nextActions": ["最多4条下一步动作"],
        "reason": "详细理由",
        "nextAction": "最推荐的一步动作",
        "convertDraft": {
            "customerName": "",
            "industry": "",
            "contactName": "",
            "contactPhone": "",
            "contactEmail": "",
            "level": "NORMAL",
            "status": "POTENTIAL",
            "remark": "",
        },
        "customerProfile": profile,
    }
    return "\n".join([
        text(request.message),
        "公开搜索客户档案：",
        json.dumps(profile, ensure_ascii=False),
        "必须返回以下字段结构：",
        json.dumps(schema_text, ensure_ascii=False),
        "线索数据：",
        json.dumps(lead, ensure_ascii=False),
    ])


def normalize_result(lead: dict[str, Any], profile: dict[str, Any], value: dict[str, Any]) -> dict[str, Any]:
    value["conclusionTitle"] = text(value.get("conclusionTitle")) or "线索分析结论"
    value["salesConclusion"] = text(value.get("salesConclusion") or value.get("summary")) or "暂无销售结论"
    value["stage"] = normalize_stage(value.get("stage") or lead.get("status"))
    value["priority"] = normalize_priority(value.get("priority"))
    value["recommendConvert"] = bool(value.get("recommendConvert"))
    value["score"] = clamp_int(value.get("score"), 0, 100)
    value["confidence"] = clamp_float(value.get("confidence"), 0.0, 1.0)
    value["keyFindings"] = normalize_string_list(value.get("keyFindings"), 4)
    value["riskWarnings"] = normalize_string_list(value.get("riskWarnings"), 4)
    value["nextActions"] = normalize_string_list(value.get("nextActions"), 4)
    value["reason"] = text(value.get("reason"))
    value["nextAction"] = text(value.get("nextAction")) or first_text(value["nextActions"])
    value["convertDraft"] = normalize_convert_draft(lead, value.get("convertDraft"))
    value["customerProfile"] = normalize_customer_profile(lead, profile, value.get("customerProfile"))
    return value


def fallback_result(lead: dict[str, Any], profile: dict[str, Any], raw_output: str) -> dict[str, Any]:
    return normalize_result(lead, profile, {
        "conclusionTitle": "未生成结构化结论",
        "salesConclusion": raw_output[:300] if raw_output else "模型未返回有效内容",
        "stage": lead.get("status") or "UNKNOWN",
        "priority": "LOW",
        "recommendConvert": False,
        "score": 0,
        "confidence": 0,
        "keyFindings": ["模型未按标准 JSON 返回，暂不能生成可靠销售动作。"],
        "riskWarnings": ["本次分析结果不建议直接作为销售决策依据。"],
        "nextActions": ["请补充线索信息后重新分析。"],
        "reason": "模型返回了非结构化内容，未生成可执行建议",
        "nextAction": "请补充线索信息后重新分析",
        "customerProfile": profile,
    })


def normalize_convert_draft(lead: dict[str, Any], value: Any) -> dict[str, Any]:
    draft = value if isinstance(value, dict) else {}
    return {
        "customerName": text(draft.get("customerName")) or text(lead.get("companyName")) or text(lead.get("name")),
        "industry": text(draft.get("industry")),
        "contactName": text(draft.get("contactName")) or text(lead.get("name")),
        "contactPhone": text(draft.get("contactPhone")) or text(lead.get("phone")),
        "contactEmail": text(draft.get("contactEmail")) or text(lead.get("email")),
        "level": text(draft.get("level")) or "NORMAL",
        "status": text(draft.get("status")) or "POTENTIAL",
        "remark": text(draft.get("remark")) or build_default_remark(lead),
    }


def normalize_customer_profile(lead: dict[str, Any], profile: dict[str, Any], value: Any) -> dict[str, Any]:
    current = dict(profile)
    if isinstance(value, dict):
        for key in current.keys():
            if key == "available":
                current[key] = bool(value.get(key, current.get(key)))
            elif key == "sourceUrls":
                current[key] = normalize_string_list(value.get(key), 6)
            else:
                current[key] = text(value.get(key)) or text(current.get(key))
    current["companyName"] = text(current.get("companyName")) or text(lead.get("companyName")) or text(lead.get("name"))
    return current


def parse_json_object(value: str) -> dict[str, Any] | None:
    text_value = text(value)
    if not text_value:
        return None
    start = text_value.find("{")
    end = text_value.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        parsed = json.loads(text_value[start:end + 1])
    except json.JSONDecodeError:
        return None
    if isinstance(parsed, dict):
        return parsed
    return None


def resolve_company_name(lead: dict[str, Any]) -> str:
    return text(lead.get("companyName"))


def build_default_remark(lead: dict[str, Any]) -> str:
    remark = text(lead.get("remark"))
    if remark:
        return f"原线索备注：{remark}"
    return ""


def normalize_stage(value: Any) -> str:
    stage = text(value).upper()
    if stage in {"NEW", "FOLLOWING", "QUALIFIED", "CONVERTED", "CLOSED", "UNKNOWN"}:
        return stage
    return "UNKNOWN"


def normalize_priority(value: Any) -> str:
    priority = text(value).upper()
    if priority in {"HIGH", "MEDIUM", "LOW"}:
        return priority
    return "MEDIUM"


def normalize_string_list(value: Any, max_size: int) -> list[str]:
    if not isinstance(value, list):
        return []
    result = []
    for item in value:
        item_text = text(item)
        if item_text:
            result.append(item_text)
        if len(result) >= max_size:
            break
    return result


def first_text(values: list[str]) -> str:
    if not values:
        return ""
    return values[0]


def clamp_int(value: Any, minimum: int, maximum: int) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError):
        return minimum
    return max(minimum, min(maximum, number))


def clamp_float(value: Any, minimum: float, maximum: float) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return minimum
    return max(minimum, min(maximum, number))


def usage_dict(value: LlmResult) -> dict[str, int]:
    return {
        "inputTokens": value.input_tokens,
        "outputTokens": value.output_tokens,
        "totalTokens": value.total_tokens,
    }
