from uuid import uuid4


def runtime_event(
    event_type: str,
    content: str | None = None,
    tool_name: str | None = None,
    metadata: dict | None = None,
) -> dict:
    return {
        "id": uuid4().hex,
        "type": event_type,
        "content": content,
        "toolName": tool_name,
        "metadata": metadata or {},
    }
