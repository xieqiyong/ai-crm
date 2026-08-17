import json
import logging
import time
from dataclasses import dataclass
from typing import Any

import httpx

from app.core.config import settings
from app.core.trace_utils import trace_llm_inputs, trace_llm_outputs, traceable
from app.runtime.streaming import emit_answer_delta, emit_thought_delta
from app.schemas.runtime import RuntimeAgent


logger = logging.getLogger("crm_ai_runtime.llm")


@dataclass
class LlmResult:
    content: str
    input_tokens: int = 0
    output_tokens: int = 0
    total_tokens: int = 0


class LlmStatusError(RuntimeError):
    def __init__(self, status_code: int, body: str):
        super().__init__(f"模型调用失败：{status_code} {shrink(body, 500)}")
        self.status_code = status_code
        self.body = body


class OpenAICompatibleClient:
    @traceable(
        run_type="llm",
        name="大模型调用",
        process_inputs=trace_llm_inputs,
        process_outputs=trace_llm_outputs,
    )
    async def chat(self, agent: RuntimeAgent, system_prompt: str, user_prompt: str) -> LlmResult:
        if agent is None:
            raise RuntimeError("智能体配置不能为空")
        if not text(agent.model_name):
            raise RuntimeError("模型名称不能为空")
        if not text(agent.api_key):
            raise RuntimeError("模型密钥未配置")

        url = self._chat_url(agent)
        timeout = max(5, settings.llm_timeout_seconds)
        start_time = time.perf_counter()
        logger.info(
            "大模型请求开始 agentId=%s sceneCode=%s provider=%s model=%s url=%s systemChars=%s userChars=%s timeoutSeconds=%s",
            text(agent.id),
            text(agent.scene_code),
            text(agent.model_provider),
            text(agent.model_name),
            mask_url(url),
            len(system_prompt or ""),
            len(user_prompt or ""),
            timeout,
        )

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

        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                response = await client.post(url, json=body, headers=headers)
            elapsed_ms = int((time.perf_counter() - start_time) * 1000)
            if response.status_code < 200 or response.status_code >= 300:
                logger.error(
                    "大模型请求失败 agentId=%s sceneCode=%s status=%s elapsedMs=%s body=%s",
                    text(agent.id),
                    text(agent.scene_code),
                    response.status_code,
                    elapsed_ms,
                    shrink(response.text, 500),
                )
                raise RuntimeError(f"模型调用失败：{response.status_code} {shrink(response.text, 500)}")

            payload = response.json()
            content = self._extract_content(payload)
            usage = payload.get("usage") or {}
            result = LlmResult(
                content=content,
                input_tokens=int_value(usage.get("prompt_tokens") or usage.get("input_tokens")),
                output_tokens=int_value(usage.get("completion_tokens") or usage.get("output_tokens")),
                total_tokens=int_value(usage.get("total_tokens")),
            )
            logger.info(
                "大模型请求完成 agentId=%s sceneCode=%s status=%s elapsedMs=%s inputTokens=%s outputTokens=%s totalTokens=%s outputChars=%s",
                text(agent.id),
                text(agent.scene_code),
                response.status_code,
                elapsed_ms,
                result.input_tokens,
                result.output_tokens,
                result.total_tokens,
                len(result.content or ""),
            )
            return result
        except Exception:
            elapsed_ms = int((time.perf_counter() - start_time) * 1000)
            logger.exception("大模型请求异常 agentId=%s sceneCode=%s elapsedMs=%s", text(agent.id), text(agent.scene_code), elapsed_ms)
            raise

    @traceable(
        run_type="llm",
        name="大模型流式调用",
        process_inputs=trace_llm_inputs,
        process_outputs=trace_llm_outputs,
    )
    async def stream_chat(self, agent: RuntimeAgent, system_prompt: str, user_prompt: str) -> LlmResult:
        try:
            return await self._stream_chat(agent, system_prompt, user_prompt, settings.llm_stream_include_usage)
        except LlmStatusError as ex:
            if not settings.llm_stream_include_usage or ex.status_code < 400:
                raise
            if not stream_options_rejected(ex.body):
                raise
            logger.info("大模型流式请求不支持usage返回，切换为普通流式模式 agentId=%s sceneCode=%s", text(agent.id), text(agent.scene_code))
            return await self._stream_chat(agent, system_prompt, user_prompt, False)

    async def _stream_chat(
            self,
            agent: RuntimeAgent,
            system_prompt: str,
            user_prompt: str,
            include_usage: bool) -> LlmResult:
        if agent is None:
            raise RuntimeError("智能体配置不能为空")
        if not text(agent.model_name):
            raise RuntimeError("模型名称不能为空")
        if not text(agent.api_key):
            raise RuntimeError("模型密钥未配置")

        url = self._chat_url(agent)
        timeout = max(5, settings.llm_timeout_seconds)
        start_time = time.perf_counter()
        logger.info(
            "大模型流式请求开始 agentId=%s sceneCode=%s provider=%s model=%s url=%s systemChars=%s userChars=%s timeoutSeconds=%s includeUsage=%s",
            text(agent.id),
            text(agent.scene_code),
            text(agent.model_provider),
            text(agent.model_name),
            mask_url(url),
            len(system_prompt or ""),
            len(user_prompt or ""),
            timeout,
            include_usage,
        )

        body = {
            "model": agent.model_name,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0.2,
            "stream": True,
        }
        if include_usage:
            body["stream_options"] = {"include_usage": True}
        headers = {
            "Authorization": f"Bearer {agent.api_key}",
            "Content-Type": "application/json",
        }

        pieces = []
        usage = {}
        first_token_ms = 0
        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                async with client.stream("POST", url, json=body, headers=headers) as response:
                    if response.status_code < 200 or response.status_code >= 300:
                        body_text = (await response.aread()).decode("utf-8", errors="ignore")
                        elapsed_ms = int((time.perf_counter() - start_time) * 1000)
                        logger.error(
                            "大模型流式请求失败 agentId=%s sceneCode=%s status=%s elapsedMs=%s body=%s",
                            text(agent.id),
                            text(agent.scene_code),
                            response.status_code,
                            elapsed_ms,
                            shrink(body_text, 500),
                        )
                        raise LlmStatusError(response.status_code, body_text)
                    async for line in response.aiter_lines():
                        payload = sse_payload(line)
                        if not payload:
                            continue
                        if payload == "[DONE]":
                            break
                        item = parse_stream_payload(payload)
                        if not item:
                            continue
                        if item.get("usage"):
                            usage = item.get("usage") or usage
                        reasoning_delta = self._extract_reasoning_delta(item)
                        if reasoning_delta:
                            await emit_thought_delta(reasoning_delta)
                        delta = self._extract_delta(item)
                        if not delta:
                            continue
                        if first_token_ms <= 0:
                            first_token_ms = int((time.perf_counter() - start_time) * 1000)
                            logger.info(
                                "大模型首个增量返回 agentId=%s sceneCode=%s firstTokenMs=%s",
                                text(agent.id),
                                text(agent.scene_code),
                                first_token_ms,
                            )
                        pieces.append(delta)
                        await emit_answer_delta(delta)
            elapsed_ms = int((time.perf_counter() - start_time) * 1000)
            content = "".join(pieces)
            result = LlmResult(
                content=content,
                input_tokens=int_value(usage.get("prompt_tokens") or usage.get("input_tokens")),
                output_tokens=int_value(usage.get("completion_tokens") or usage.get("output_tokens")),
                total_tokens=int_value(usage.get("total_tokens")),
            )
            logger.info(
                "大模型流式请求完成 agentId=%s sceneCode=%s elapsedMs=%s firstTokenMs=%s inputTokens=%s outputTokens=%s totalTokens=%s outputChars=%s",
                text(agent.id),
                text(agent.scene_code),
                elapsed_ms,
                first_token_ms,
                result.input_tokens,
                result.output_tokens,
                result.total_tokens,
                len(result.content or ""),
            )
            return result
        except LlmStatusError:
            raise
        except Exception:
            elapsed_ms = int((time.perf_counter() - start_time) * 1000)
            logger.exception("大模型流式请求异常 agentId=%s sceneCode=%s elapsedMs=%s", text(agent.id), text(agent.scene_code), elapsed_ms)
            raise

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

    def _extract_delta(self, payload: dict[str, Any]) -> str:
        choices = payload.get("choices") or []
        if not choices:
            return ""
        choice = choices[0] or {}
        delta = choice.get("delta") or {}
        if isinstance(delta, dict):
            return text(delta.get("content"))
        message = choice.get("message") or {}
        if isinstance(message, dict):
            return text(message.get("content"))
        return ""

    def _extract_reasoning_delta(self, payload: dict[str, Any]) -> str:
        choices = payload.get("choices") or []
        if not choices:
            return ""
        choice = choices[0] or {}
        delta = choice.get("delta") or {}
        if isinstance(delta, dict):
            value = (
                delta.get("reasoning_content")
                or delta.get("reasoningContent")
                or delta.get("reasoning")
                or delta.get("thought")
            )
            if value:
                return text(value)
        message = choice.get("message") or {}
        if isinstance(message, dict):
            value = (
                message.get("reasoning_content")
                or message.get("reasoningContent")
                or message.get("reasoning")
                or message.get("thought")
            )
            if value:
                return text(value)
        return ""


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


def mask_url(value: str) -> str:
    text_value = text(value)
    if "?" not in text_value:
        return text_value
    return text_value.split("?", 1)[0] + "?***"


def sse_payload(line: str) -> str:
    value = text(line)
    if not value or value.startswith(":"):
        return ""
    if value.startswith("data:"):
        return value[5:].strip()
    if value.startswith("{"):
        return value
    return ""


def parse_stream_payload(value: str) -> dict[str, Any] | None:
    try:
        payload = json.loads(value)
    except json.JSONDecodeError:
        return None
    return payload if isinstance(payload, dict) else None


def stream_options_rejected(value: str) -> bool:
    text_value = text(value).lower()
    return (
        "stream_options" in text_value
        or "include_usage" in text_value
        or "unknown parameter" in text_value
        or "unsupported" in text_value
        or "unrecognized" in text_value
    )
