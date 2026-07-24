from uuid import uuid4

from app.schemas.runtime import RuntimeEvent


def runtime_event(
    event_type: str,
    content: str | None = None,
    tool_name: str | None = None,
    metadata: dict | None = None,
) -> RuntimeEvent:
    return RuntimeEvent(
        id=uuid4().hex,
        type=event_type,
        content=content,
        tool_name=tool_name,
        metadata=metadata or {},
    )
