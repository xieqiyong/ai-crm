from typing import Any

from langchain.messages import AIMessageChunk
from langchain_openai import ChatOpenAI


class OpenAICompatibleChatModel(ChatOpenAI):
    def _convert_chunk_to_generation_chunk(
            self,
            chunk: dict,
            default_chunk_class: type,
            base_generation_info: dict | None):
        generation_chunk = super()._convert_chunk_to_generation_chunk(
            chunk,
            default_chunk_class,
            base_generation_info,
        )
        if generation_chunk is None or not isinstance(generation_chunk.message, AIMessageChunk):
            return generation_chunk
        reasoning = self._reasoning_delta(chunk)
        if reasoning:
            generation_chunk.message.additional_kwargs["reasoning_content"] = reasoning
        return generation_chunk

    def _reasoning_delta(self, chunk: dict[str, Any]) -> str:
        choices = chunk.get("choices") or chunk.get("chunk", {}).get("choices") or []
        if not choices or not isinstance(choices[0], dict):
            return ""
        delta = choices[0].get("delta") or {}
        if not isinstance(delta, dict):
            return ""
        for key in ("reasoning_content", "reasoningContent", "reasoning", "thought"):
            value = self._reasoning_text(delta.get(key))
            if value:
                return value
        return ""

    def _reasoning_text(self, value: Any) -> str:
        if isinstance(value, str):
            return value
        if isinstance(value, dict):
            for key in ("text", "summary", "content"):
                text = self._reasoning_text(value.get(key))
                if text:
                    return text
            return ""
        if isinstance(value, list):
            return "".join(self._reasoning_text(item) for item in value)
        return ""
