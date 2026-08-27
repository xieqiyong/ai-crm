import json
import logging
from typing import Any

from app.core.config import settings
from app.models.openai_compatible import OpenAICompatibleChatModel
from app.schemas.runtime import RuntimeAgent

logger = logging.getLogger("crm_ai_runtime.model.factory")


class ChatModelFactory:
    def build(
            self,
            agent: RuntimeAgent,
            force_tool_choice: bool = False) -> OpenAICompatibleChatModel:
        if agent is None:
            raise RuntimeError("智能体配置不能为空")
        model_name = self._text(agent.model_name)
        api_key = self._text(agent.api_key)
        if not model_name:
            raise RuntimeError("智能体未配置模型名称")
        if not api_key:
            raise RuntimeError("智能体未配置模型密钥")

        config = self._extra_config(agent.extra_config_json)
        temperature = config.get("temperature", 0.2)
        if temperature is not None:
            temperature = float(temperature)
        max_completion_tokens = self._positive_int(
            config.get("maxCompletionTokens") or config.get("max_completion_tokens"))
        timeout = float(config.get("timeoutSeconds") or settings.llm_timeout_seconds)
        max_retries = self._non_negative_int(config.get("maxRetries"), 2)
        stream_usage = self._bool(config.get("streamUsage"), settings.llm_stream_include_usage)
        extra_body = self._extra_body(config, model_name, force_tool_choice)
        reasoning_effort = self._text(config.get("reasoningEffort")) or None
        if force_tool_choice and self._is_deepseek_v4(model_name):
            reasoning_effort = None
            logger.info(
                "DeepSeek V4结构化输出已关闭Thinking，以兼容强制工具选择，model=%s",
                model_name,
            )

        return OpenAICompatibleChatModel(
            model=model_name,
            api_key=api_key,
            base_url=self._base_url(agent),
            temperature=temperature,
            max_completion_tokens=max_completion_tokens,
            timeout=timeout,
            max_retries=max_retries,
            streaming=True,
            stream_usage=stream_usage,
            output_version="v1",
            reasoning_effort=reasoning_effort,
            extra_body=extra_body,
            tags=["crm-agent", (agent.scene_code or "general_assistant").lower()],
            metadata={
                "agentId": agent.id or "",
                "sceneCode": agent.scene_code or "",
                "modelProvider": agent.model_provider or "",
            },
        )

    def _extra_body(
            self,
            config: dict[str, Any],
            model_name: str,
            force_tool_choice: bool) -> dict[str, Any] | None:
        value = config.get("extraBody")
        result = dict(value) if isinstance(value, dict) else {}
        if force_tool_choice and self._is_deepseek_v4(model_name):
            result["thinking"] = {"type": "disabled"}
        return result or None

    def _is_deepseek_v4(self, model_name: str) -> bool:
        return model_name.strip().lower().startswith("deepseek-v4")

    def _base_url(self, agent: RuntimeAgent) -> str:
        value = self._text(agent.base_url) or self._default_base_url(agent.model_provider)
        value = value.rstrip("/")
        suffix = "/chat/completions"
        if value.endswith(suffix):
            value = value[:-len(suffix)]
        return value

    def _default_base_url(self, provider: str | None) -> str:
        normalized = self._text(provider).upper()
        if normalized == "DEEPSEEK":
            return "https://api.deepseek.com/v1"
        if normalized == "DASHSCOPE":
            return "https://dashscope.aliyuncs.com/compatible-mode/v1"
        return "https://api.openai.com/v1"

    def _extra_config(self, value: str | None) -> dict[str, Any]:
        if not self._text(value):
            return {}
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError as ex:
            raise RuntimeError("智能体附加配置不是合法JSON") from ex
        if not isinstance(parsed, dict):
            raise RuntimeError("智能体附加配置必须是JSON对象")
        return parsed

    def _positive_int(self, value: Any) -> int | None:
        try:
            number = int(value)
        except (TypeError, ValueError):
            return None
        return number if number > 0 else None

    def _non_negative_int(self, value: Any, default: int) -> int:
        try:
            return max(int(value), 0)
        except (TypeError, ValueError):
            return default

    def _bool(self, value: Any, default: bool) -> bool:
        if value is None:
            return default
        if isinstance(value, bool):
            return value
        return str(value).strip().lower() in {"true", "1", "yes", "on"}

    def _text(self, value: Any) -> str:
        if value is None:
            return ""
        return str(value).strip()


chat_model_factory = ChatModelFactory()
