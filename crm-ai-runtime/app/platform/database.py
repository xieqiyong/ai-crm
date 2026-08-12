import asyncio
import logging
from typing import Any

import psycopg
from psycopg.rows import dict_row

from app.core.config import settings

logger = logging.getLogger(__name__)


class DatabaseClient:
    def enabled(self) -> bool:
        return settings.database_enabled and bool(settings.database_uri.strip())

    async def fetch_one(self, sql: str, params: tuple[Any, ...]) -> dict[str, Any] | None:
        if not self.enabled():
            return None
        return await asyncio.to_thread(self._fetch_one_sync, sql, params)

    async def fetch_all(self, sql: str, params: tuple[Any, ...]) -> list[dict[str, Any]]:
        if not self.enabled():
            return []
        return await asyncio.to_thread(self._fetch_all_sync, sql, params)

    def _fetch_one_sync(self, sql: str, params: tuple[Any, ...]) -> dict[str, Any] | None:
        with psycopg.connect(settings.database_uri, row_factory=dict_row) as conn:
            with conn.cursor() as cursor:
                cursor.execute(sql, params)
                value = cursor.fetchone()
                return dict(value) if value else None

    def _fetch_all_sync(self, sql: str, params: tuple[Any, ...]) -> list[dict[str, Any]]:
        with psycopg.connect(settings.database_uri, row_factory=dict_row) as conn:
            with conn.cursor() as cursor:
                cursor.execute(sql, params)
                values = cursor.fetchall()
                return [dict(item) for item in values]


database_client = DatabaseClient()
