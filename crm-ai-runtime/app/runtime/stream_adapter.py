"""LangGraph运行流到产品SSE协议的适配器。"""

from typing import Any

from langchain.messages import AIMessage, AIMessageChunk, ToolMessage
from pydantic import BaseModel

from app.runtime.streaming import emit_answer_delta, emit_runtime_event, emit_runtime_status, emit_thought_delta
from app.services.event_bus import runtime_event


class AgentStreamAccumulator:
    def __init__(self):
        self.latest_state: dict[str, Any] = {}
        self.events: list[dict[str, Any]] = []
        self._usage_by_message: dict[str, dict[str, int]] = {}
        self._tool_event_ids: set[str] = set()

    async def consume(self, part: dict[str, Any]) -> None:
        part_type = part.get("type")
        data = part.get("data")
        if part_type == "messages":
            await self._consume_message_part(data)
        elif part_type == "custom":
            await self._consume_custom_part(data)
        elif part_type == "updates":
            await self._consume_updates(data)
        elif part_type == "values" and isinstance(data, dict):
            self.latest_state = data

    def output(self) -> str:
        structured = self.latest_state.get("structured_response")
        if structured is not None:
            return self._structured_json(structured)
        messages = self.latest_state.get("messages") or []
        for message in reversed(messages):
            if isinstance(message, AIMessage) and not message.tool_calls:
                content = self._message_text(message)
                if content:
                    return content
        raise RuntimeError("智能体运行结束，但没有生成最终回复")

    def usage(self) -> dict[str, int]:
        input_tokens = sum(value.get("inputTokens", 0) for value in self._usage_by_message.values())
        output_tokens = sum(value.get("outputTokens", 0) for value in self._usage_by_message.values())
        total_tokens = sum(value.get("totalTokens", 0) for value in self._usage_by_message.values())
        return {
            "inputTokens": input_tokens,
            "outputTokens": output_tokens,
            "totalTokens": total_tokens or input_tokens + output_tokens,
        }

    def finish_events(self, output: str) -> list[dict[str, Any]]:
        usage = self.usage()
        values = list(self.events)
        values.append(runtime_event("FINAL_RESULT", content=output, metadata={"status": "SUCCESS", **usage}))
        return values

    async def _consume_message_part(self, data: Any) -> None:
        if not isinstance(data, tuple) or len(data) != 2:
            return
        message, metadata = data
        self._capture_usage(message, metadata if isinstance(metadata, dict) else {})
        if not isinstance(message, AIMessageChunk):
            return
        answer_parts, thought_parts = self._chunk_parts(message)
        for content in thought_parts:
            await emit_thought_delta(content)
        for content in answer_parts:
            await emit_answer_delta(content)

    async def _consume_custom_part(self, data: Any) -> None:
        if isinstance(data, str):
            await emit_runtime_status(data)
            return
        if not isinstance(data, dict):
            return
        content = str(data.get("content") or "").strip()
        if not content:
            return
        event_type = str(data.get("type") or "").strip().upper()
        metadata = data.get("metadata") if isinstance(data.get("metadata"), dict) else {}
        if event_type == "REPORT_READY":
            self.events.append(runtime_event(
                "REPORT_READY",
                content=content,
                tool_name=str(data.get("toolName") or "generate_report"),
                metadata=metadata,
            ))
            await emit_runtime_event(
                "REPORT_READY",
                content,
                "报告生成完成",
                metadata,
            )
            return
        if data.get("type") == "GRAPH_NODE_STATUS" and str(data.get("status") or "").upper() != "RUNNING":
            self.events.append(runtime_event(
                "STEP",
                content=content,
                metadata={
                    "node": data.get("node"),
                    "nodeName": data.get("nodeName"),
                    "status": data.get("status"),
                    "elapsedMs": data.get("elapsedMs"),
                },
            ))
        await emit_runtime_status(content, {
            "toolName": data.get("toolName"),
            "source": "langgraph_custom_stream",
            "node": data.get("node"),
            "status": data.get("status"),
            "elapsedMs": data.get("elapsedMs"),
        })

    async def _consume_updates(self, data: Any) -> None:
        if not isinstance(data, dict):
            return
        for node_name, update in data.items():
            if not isinstance(update, dict):
                continue
            messages = update.get("messages") or []
            if not isinstance(messages, list):
                messages = [messages]
            for message in messages:
                self._capture_usage(message, {"langgraph_node": node_name})
                if isinstance(message, ToolMessage):
                    await self._record_tool_message(message)

    async def _record_tool_message(self, message: ToolMessage) -> None:
        event_id = str(message.tool_call_id or message.id or "")
        if event_id and event_id in self._tool_event_ids:
            return
        if event_id:
            self._tool_event_ids.add(event_id)
        tool_name = str(message.name or "unknown")
        status = str(getattr(message, "status", None) or "success").upper()
        event_status = "FAILED" if status == "ERROR" else "SUCCESS"
        self.events.append(runtime_event(
            "TOOL",
            content="工具执行失败" if event_status == "FAILED" else "工具执行完成",
            tool_name=tool_name,
            metadata={
                "status": event_status,
                "toolCallId": message.tool_call_id,
            },
        ))
        await emit_runtime_status(
            "辅助能力执行失败" if event_status == "FAILED" else "辅助资料已获取",
            {"toolName": tool_name, "status": event_status},
        )

    def _capture_usage(self, message: Any, metadata: dict[str, Any]) -> None:
        usage = getattr(message, "usage_metadata", None) or {}
        if not usage:
            return
        key = str(getattr(message, "id", None) or metadata.get("run_id") or id(message))
        input_tokens = int(usage.get("input_tokens") or 0)
        output_tokens = int(usage.get("output_tokens") or 0)
        total_tokens = int(usage.get("total_tokens") or 0) or input_tokens + output_tokens
        current = self._usage_by_message.get(key) or {}
        self._usage_by_message[key] = {
            "inputTokens": max(input_tokens, current.get("inputTokens", 0)),
            "outputTokens": max(output_tokens, current.get("outputTokens", 0)),
            "totalTokens": max(total_tokens, current.get("totalTokens", 0)),
        }

    def _chunk_parts(self, message: AIMessageChunk) -> tuple[list[str], list[str]]:
        answer_parts: list[str] = []
        thought_parts: list[str] = []
        blocks = getattr(message, "content_blocks", None) or []
        for block in blocks:
            if not isinstance(block, dict):
                continue
            block_type = str(block.get("type") or "").lower()
            if block_type == "text":
                content = str(block.get("text") or "")
                if content:
                    answer_parts.append(content)
            elif block_type in {"reasoning", "reasoning_content", "thinking"}:
                content = self._reasoning_text(block)
                if content:
                    thought_parts.append(content)
        additional_reasoning = self._message_reasoning(message)
        if additional_reasoning and additional_reasoning not in thought_parts:
            thought_parts.append(additional_reasoning)
        if not blocks and isinstance(message.content, str) and message.content:
            answer_parts.append(message.content)
        return answer_parts, thought_parts

    def _message_reasoning(self, message: AIMessageChunk) -> str:
        values = [
            message.additional_kwargs.get("reasoning_content"),
            message.additional_kwargs.get("reasoningContent"),
            message.additional_kwargs.get("reasoning"),
            message.additional_kwargs.get("thought"),
            message.response_metadata.get("reasoning_content"),
            message.response_metadata.get("reasoning"),
        ]
        for value in values:
            if isinstance(value, str) and value:
                return value
            if isinstance(value, dict):
                content = self._reasoning_text(value)
                if content:
                    return content
        return ""

    def _reasoning_text(self, block: dict[str, Any]) -> str:
        for key in ("text", "reasoning", "summary", "content"):
            value = block.get(key)
            if isinstance(value, str) and value:
                return value
            if isinstance(value, list):
                texts = []
                for item in value:
                    if isinstance(item, str):
                        texts.append(item)
                    elif isinstance(item, dict):
                        text = item.get("text") or item.get("summary")
                        if text:
                            texts.append(str(text))
                if texts:
                    return "".join(texts)
        return ""

    def _message_text(self, message: AIMessage) -> str:
        text = getattr(message, "text", None)
        if isinstance(text, str) and text:
            return text
        if isinstance(message.content, str):
            return message.content
        values = []
        for item in message.content or []:
            if isinstance(item, dict) and item.get("type") == "text" and item.get("text"):
                values.append(str(item.get("text")))
        return "".join(values)

    def _structured_json(self, value: Any) -> str:
        if isinstance(value, BaseModel):
            return value.model_dump_json(by_alias=True)
        if isinstance(value, dict):
            import json
            return json.dumps(value, ensure_ascii=False, default=str)
        raise RuntimeError("智能体结构化输出类型不正确")
