import json
from typing import Any

from pydantic import BaseModel, Field, ValidationError


class FunctionCallResult(BaseModel):
    name: str
    arguments: dict[str, Any] = Field(default_factory=dict)


def extract_json_object(value: str) -> dict[str, Any] | None:
    text = (value or "").strip()
    if not text:
        return None
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        data = json.loads(text[start:end + 1])
    except json.JSONDecodeError:
        return None
    return data if isinstance(data, dict) else None


def parse_function_call(value: str) -> FunctionCallResult | None:
    data = extract_json_object(value)
    if data is None:
        return None
    try:
        return FunctionCallResult.model_validate(data)
    except ValidationError:
        return None
