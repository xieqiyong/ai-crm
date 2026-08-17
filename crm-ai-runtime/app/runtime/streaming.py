import asyncio
from contextvars import ContextVar
from dataclasses import dataclass, field
from typing import Any


STREAM_END = object()


@dataclass
class RuntimeStream:
    queue: asyncio.Queue = field(default_factory=asyncio.Queue)
    closed: bool = False

    async def emit(self, event_type: str, content: str = "", stage: str = "", metadata: dict[str, Any] | None = None) -> None:
        if self.closed:
            return
        await self.queue.put({
            "type": event_type,
            "stage": stage,
            "content": content,
            "metadata": metadata or {},
        })

    async def close(self) -> None:
        if self.closed:
            return
        self.closed = True
        await self.queue.put(STREAM_END)


current_runtime_stream: ContextVar[RuntimeStream | None] = ContextVar("current_runtime_stream", default=None)


def set_runtime_stream(stream: RuntimeStream):
    return current_runtime_stream.set(stream)


def reset_runtime_stream(token) -> None:
    current_runtime_stream.reset(token)


def runtime_streaming_enabled() -> bool:
    return current_runtime_stream.get() is not None


async def emit_answer_delta(content: str, metadata: dict[str, Any] | None = None) -> None:
    if not content:
        return
    stream = current_runtime_stream.get()
    if stream is None:
        return
    await stream.emit("ANSWER_DELTA", content, "回答增量", metadata or {})


async def emit_thought_delta(content: str, metadata: dict[str, Any] | None = None) -> None:
    if not content:
        return
    stream = current_runtime_stream.get()
    if stream is None:
        return
    await stream.emit("THOUGHT_DELTA", content, "模型推理摘要", metadata or {})


async def emit_runtime_status(content: str, metadata: dict[str, Any] | None = None) -> None:
    if not content:
        return
    stream = current_runtime_stream.get()
    if stream is None:
        return
    await stream.emit("RUN_STATUS_CHANGED", content, "执行进度", metadata or {})
