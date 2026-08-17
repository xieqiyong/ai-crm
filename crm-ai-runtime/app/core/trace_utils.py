import os
from typing import Any

from app.core.config import settings

try:
    from langsmith import traceable as langsmith_traceable
except ImportError:
    langsmith_traceable = None


SENSITIVE_KEYS = {
    "apiKey",
    "api_key",
    "apikey",
    "authorization",
    "password",
    "secret",
    "token",
    "x-api-key",
    "X-API-KEY",
}


def traceable(*args, **kwargs):
    if langsmith_traceable is None:
        def decorator(func):
            return func

        return decorator
    return langsmith_traceable(*args, **kwargs)


def trace_enabled() -> bool:
    value = os.environ.get("LANGSMITH_TRACING") or os.environ.get("LANGCHAIN_TRACING_V2")
    return str(value or "").strip().lower() in {"1", "true", "yes", "on"}


def runtime_trace_metadata(request: Any) -> dict[str, Any]:
    value = to_dict(request)
    context = to_dict(value.get("context"))
    agent = to_dict(value.get("agent"))
    return {
        "tenantId": text(value.get("tenantId") or value.get("tenant_id")),
        "userId": text(value.get("userId") or value.get("user_id")),
        "sceneCode": text(value.get("sceneCode") or value.get("scene_code")),
        "businessType": text(value.get("businessType") or value.get("business_type")),
        "businessId": text(value.get("businessId") or value.get("business_id")),
        "runId": text(value.get("runId") or value.get("run_id")),
        "conversationId": text(value.get("conversationId") or value.get("conversation_id")),
        "sessionId": text(value.get("sessionId") or value.get("session_id")),
        "traceId": text(context.get("traceId") or context.get("trace_id")),
        "agentCode": text(agent.get("code")),
        "agentName": text(agent.get("name")),
        "modelProvider": text(agent.get("modelProvider") or agent.get("model_provider")),
        "modelName": text(agent.get("modelName") or agent.get("model_name")),
    }


def runtime_trace_tags(request: Any) -> list[str]:
    value = to_dict(request)
    scene_code = text(value.get("sceneCode") or value.get("scene_code")).lower()
    tags = ["crm", "ai-runtime"]
    if scene_code:
        tags.append(scene_code)
    return tags


def trace_runtime_inputs(inputs: dict[str, Any]) -> dict[str, Any]:
    request = extract_value(inputs, "request", 1)
    value = to_dict(request)
    agent = to_dict(value.get("agent"))
    context = to_dict(value.get("context"))
    result = {
        "tenantId": text(value.get("tenantId") or value.get("tenant_id")),
        "userId": text(value.get("userId") or value.get("user_id")),
        "sceneCode": text(value.get("sceneCode") or value.get("scene_code")),
        "businessType": text(value.get("businessType") or value.get("business_type")),
        "businessId": text(value.get("businessId") or value.get("business_id")),
        "runId": text(value.get("runId") or value.get("run_id")),
        "conversationId": text(value.get("conversationId") or value.get("conversation_id")),
        "sessionId": text(value.get("sessionId") or value.get("session_id")),
        "traceId": text(context.get("traceId") or context.get("trace_id")),
        "messageLength": len(text(value.get("message"))),
        "contextKeys": sorted([str(key) for key in context.keys()]),
        "skills": resource_names(value.get("skills")),
        "mcps": resource_names(value.get("mcps")),
        "agent": {
            "id": text(agent.get("id")),
            "code": text(agent.get("code")),
            "name": text(agent.get("name")),
            "sceneCode": text(agent.get("sceneCode") or agent.get("scene_code")),
            "modelProvider": text(agent.get("modelProvider") or agent.get("model_provider")),
            "modelName": text(agent.get("modelName") or agent.get("model_name")),
            "baseUrl": text(agent.get("baseUrl") or agent.get("base_url")),
        },
    }
    if settings.trace_capture_payload:
        result["message"] = shrink(value.get("message"), 2000)
        result["lead"] = clean_for_trace(context.get("lead"))
    return result


def trace_runtime_outputs(output: Any) -> dict[str, Any]:
    value = to_dict(output)
    result = {
        "success": bool(value.get("success", False)),
        "runId": text(value.get("runId") or value.get("run_id")),
        "conversationId": text(value.get("conversationId") or value.get("conversation_id")),
        "threadId": text(value.get("threadId") or value.get("thread_id")),
        "checkpointEnabled": bool(value.get("checkpointEnabled") or value.get("checkpoint_enabled")),
        "eventCount": len(value.get("events") or []),
        "outputLength": len(text(value.get("output"))),
    }
    if settings.trace_capture_payload:
        result["output"] = shrink(value.get("output"), 2000)
    return result


def trace_llm_inputs(inputs: dict[str, Any]) -> dict[str, Any]:
    agent = extract_value(inputs, "agent", 1)
    if not agent:
        agent = extract_value(inputs, "agent", 0)
    agent_value = to_dict(agent)
    system_prompt = extract_value(inputs, "system_prompt", 2)
    user_prompt = extract_value(inputs, "user_prompt", 3)
    if system_prompt is None:
        system_prompt = extract_value(inputs, "system_prompt", 1)
    if user_prompt is None:
        user_prompt = extract_value(inputs, "user_prompt", 2)
    result = {
        "modelProvider": text(agent_value.get("modelProvider") or agent_value.get("model_provider")),
        "modelName": text(agent_value.get("modelName") or agent_value.get("model_name")),
        "baseUrl": text(agent_value.get("baseUrl") or agent_value.get("base_url")),
        "systemPromptLength": len(text(system_prompt)),
        "userPromptLength": len(text(user_prompt)),
    }
    if settings.trace_capture_payload:
        result["systemPrompt"] = shrink(system_prompt, 2000)
        result["userPrompt"] = shrink(user_prompt, 3000)
    return result


def trace_llm_outputs(output: Any) -> dict[str, Any]:
    value = to_dict(output)
    result = {
        "inputTokens": int_value(value.get("input_tokens") or value.get("inputTokens")),
        "outputTokens": int_value(value.get("output_tokens") or value.get("outputTokens")),
        "totalTokens": int_value(value.get("total_tokens") or value.get("totalTokens")),
        "contentLength": len(text(value.get("content"))),
    }
    if settings.trace_capture_payload:
        result["content"] = shrink(value.get("content"), 2000)
    return result


def trace_search_inputs(inputs: dict[str, Any]) -> dict[str, Any]:
    company_name = extract_value(inputs, "company_name", 1)
    if company_name is None:
        company_name = extract_value(inputs, "company_name", 0)
    return {
        "companyName": text(company_name),
        "provider": settings.web_search_provider,
        "enabled": settings.web_search_enabled,
        "endpointConfigured": bool(text(settings.web_search_endpoint)),
    }


def trace_search_outputs(output: Any) -> dict[str, Any]:
    value = to_dict(output)
    return {
        "available": bool(value.get("available")),
        "companyName": text(value.get("companyName")),
        "sourceUrlCount": len(value.get("sourceUrls") or []),
        "summaryLength": len(text(value.get("sourceSummary"))),
    }


def trace_node_inputs(inputs: dict[str, Any]) -> dict[str, Any]:
    state = extract_value(inputs, "state", 0)
    value = to_dict(state)
    request = to_dict(value.get("request"))
    lead = to_dict(value.get("lead"))
    profile = to_dict(value.get("customer_profile"))
    context = to_dict(request.get("context"))
    result = {
        "stateKeys": sorted([str(key) for key in value.keys()]),
        "sceneCode": text(request.get("sceneCode") or request.get("scene_code")),
        "businessType": text(request.get("businessType") or request.get("business_type")),
        "businessId": text(request.get("businessId") or request.get("business_id")),
        "runId": text(request.get("runId") or request.get("run_id")),
        "conversationId": text(request.get("conversationId") or request.get("conversation_id")),
        "traceId": text(context.get("traceId") or context.get("trace_id")),
        "leadId": text(lead.get("id")),
        "companyName": text(lead.get("companyName") or lead.get("company_name")),
        "customerProfileAvailable": bool(profile.get("available")),
        "rawOutputLength": len(text(value.get("raw_output"))),
        "outputLength": len(text(value.get("output"))),
    }
    if settings.trace_capture_payload:
        result["lead"] = clean_for_trace(lead)
        result["customerProfile"] = clean_for_trace(profile)
    return result


def trace_node_outputs(output: Any) -> dict[str, Any]:
    value = to_dict(output)
    profile = to_dict(value.get("customer_profile"))
    result = {
        "outputKeys": sorted([str(key) for key in value.keys()]),
        "eventCount": len(value.get("events") or []),
        "customerProfileAvailable": bool(profile.get("available")),
        "sourceUrlCount": len(profile.get("sourceUrls") or []),
        "rawOutputLength": len(text(value.get("raw_output"))),
        "outputLength": len(text(value.get("output"))),
        "usage": clean_for_trace(value.get("usage") or {}),
    }
    if settings.trace_capture_payload:
        result["customerProfile"] = clean_for_trace(profile)
        result["output"] = shrink(value.get("output"), 2000)
    return result


def clean_for_trace(value: Any) -> Any:
    if isinstance(value, dict):
        result = {}
        for key, item in value.items():
            if sensitive_key(str(key)):
                result[key] = "已脱敏"
            else:
                result[key] = clean_for_trace(item)
        return result
    if isinstance(value, list):
        return [clean_for_trace(item) for item in value[:20]]
    if isinstance(value, str):
        return shrink(value, 1000)
    if value is None or isinstance(value, (int, float, bool)):
        return value
    return shrink(value, 1000)


def resource_names(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    result = []
    for item in value:
        item_value = to_dict(item)
        name = text(item_value.get("name")) or text(item_value.get("code"))
        if name:
            result.append(name)
    return result


def extract_value(inputs: dict[str, Any], name: str, position: int) -> Any:
    if not isinstance(inputs, dict):
        return None
    if name in inputs:
        return inputs.get(name)
    kwargs = inputs.get("kwargs")
    if isinstance(kwargs, dict) and name in kwargs:
        return kwargs.get(name)
    args = inputs.get("args")
    if isinstance(args, (list, tuple)) and len(args) > position:
        return args[position]
    return None


def to_dict(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if hasattr(value, "model_dump"):
        return value.model_dump(by_alias=True, mode="json")
    if hasattr(value, "__dict__"):
        return dict(value.__dict__)
    return {}


def sensitive_key(value: str) -> bool:
    lowered = value.lower()
    for key in SENSITIVE_KEYS:
        if key.lower() in lowered:
            return True
    return False


def text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def shrink(value: Any, max_length: int) -> str:
    text_value = text(value)
    if len(text_value) <= max_length:
        return text_value
    return text_value[:max_length]


def int_value(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0
