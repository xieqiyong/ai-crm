from dataclasses import dataclass
from typing import Any


@dataclass
class MemoryItem:
    key: str
    content: str
    metadata: dict[str, Any]


class MemoryStore:
    async def search(
            self,
            tenant_id: str,
            user_id: str,
            agent_id: str | None,
            query: str,
            limit: int = 5) -> list[MemoryItem]:
        return []

    async def save(
            self,
            tenant_id: str,
            user_id: str,
            agent_id: str | None,
            key: str,
            content: str,
            metadata: dict[str, Any]) -> None:
        return None


memory_store = MemoryStore()
