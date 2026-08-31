import json
from typing import Any


def parse_agent_config(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return dict(value)
    if value is None or not str(value).strip():
        return {}
    try:
        parsed = json.loads(str(value))
    except (TypeError, ValueError, json.JSONDecodeError):
        return {}
    return parsed if isinstance(parsed, dict) else {}


def agent_config(agent: Any) -> dict[str, Any]:
    if agent is None:
        return {}
    value = getattr(agent, "extra_config_json", None)
    return parse_agent_config(value)


def workflow_code(agent: Any, scene_code: str | None = None) -> str:
    config = agent_config(agent)
    configured = str(config.get("workflowCode") or config.get("workflow_code") or "").strip().upper()
    if configured:
        return configured
    if str(scene_code or "").strip().upper() == "LEAD_ANALYZE":
        return "LEAD_ANALYSIS"
    return "STANDARD_AGENT"
