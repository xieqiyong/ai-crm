import json
from dataclasses import dataclass
from typing import Any

import httpx

from app.core.config import settings
from app.schemas.runtime import RuntimeAgent


@dataclass
class LlmResult:
    content: str
    input_tokens: int = 0
    output_tokens: int = 0
    total_tokens: int = 0


class OpenAICompatibleClient:
    async def chat(self, agent: RuntimeAgent, system_prompt: str, user_prompt: str) -> LlmResult:
        if agent is None:
            raise RuntimeError("智能体配置不能为空")
        if not text(agent.model_name):
            raise RuntimeError("模型名称不能为空")
        if not text(agent.api_key):
            raise RuntimeError("模型密钥未配置")
        url = self._chat_url(agent)
        body = {
            "model": agent.model_name,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0.2,
            "stream": False,
        }
        headers = {
            "Authorization": f"Bearer {agent.api_key}",
            "Content-Type": "application/json",
        }
        timeout = max(5, settings.llm_timeout_seconds)
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(url, json=body, headers=headers)
        if response.status_code < 200 or response.status_code >= 300:
            raise RuntimeError(f"模型调用失败：{response.status_code} {shrink(response.text, 500)}")
        payload = response.json()
        content = self._extract_content(payload)
        usage = payload.get("usage") or {}
        return LlmResult(
            content=content,
            input_tokens=int_value(usage.get("prompt_tokens") or usage.get("input_tokens")),
            output_tokens=int_value(usage.get("completion_tokens") or usage.get("output_tokens")),
            total_tokens=int_value(usage.get("total_tokens")),
        )

    def _chat_url(self, agent: RuntimeAgent) -> str:
        base_url = text(agent.base_url) or default_base_url(agent.model_provider)
        base_url = base_url.rstrip("/")
        if base_url.endswith("/chat/completions"):
            return base_url
        return f"{base_url}/chat/completions"

    def _extract_content(self, payload: dict[str, Any]) -> str:
        choices = payload.get("choices") or []
        if not choices:
            return json.dumps(payload, ensure_ascii=False)
        message = (choices[0] or {}).get("message") or {}
        return text(message.get("content")) or json.dumps(payload, ensure_ascii=False)


def default_base_url(provider: str | None) -> str:
    provider_text = text(provider).upper()
    if provider_text == "DEEPSEEK":
        return "https://api.deepseek.com/v1"
    if provider_text == "DASHSCOPE":
        return "https://dashscope.aliyuncs.com/compatible-mode/v1"
    return "https://api.openai.com/v1"


def text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def int_value(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def shrink(value: str, max_length: int) -> str:
    text_value = value or ""
    if len(text_value) <= max_length:
        return text_value
    return text_value[:max_length]
