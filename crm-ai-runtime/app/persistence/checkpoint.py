import logging
from typing import Any

from langgraph.checkpoint.memory import InMemorySaver

from app.core.config import settings

logger = logging.getLogger(__name__)


class CheckpointManager:
    def __init__(self):
        self._checkpointer: Any = None
        self._pool: Any = None

    async def start(self) -> None:
        if not settings.checkpoint_enabled:
            logger.info("LangGraph checkpoint未启用")
            return
        backend = self._backend()
        if backend == "memory":
            self._checkpointer = InMemorySaver()
            logger.info("LangGraph checkpoint已启用，存储类型：memory")
            return
        if backend != "postgres":
            raise RuntimeError("暂不支持的checkpoint类型：%s" % backend)
        if not settings.checkpoint_postgres_uri.strip():
            raise RuntimeError("PostgreSQL checkpoint地址未配置")
        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        from psycopg_pool import AsyncConnectionPool

        min_size = max(int(settings.checkpoint_pool_min_size or 1), 1)
        max_size = max(int(settings.checkpoint_pool_max_size or 10), min_size)
        timeout = max(float(settings.checkpoint_pool_timeout_seconds or 30), 5.0)
        self._pool = AsyncConnectionPool(
            conninfo=settings.checkpoint_postgres_uri,
            min_size=min_size,
            max_size=max_size,
            timeout=timeout,
            kwargs={
                "autocommit": True,
                "prepare_threshold": 0,
            },
            open=False,
            name="crm-ai-checkpoint",
        )
        await self._pool.open(wait=True, timeout=timeout)
        self._checkpointer = AsyncPostgresSaver(self._pool)
        logger.info(
            "LangGraph checkpoint已启用，存储类型：postgres，连接池：%s-%s",
            min_size,
            max_size,
        )

    async def stop(self) -> None:
        if self._pool is not None:
            await self._pool.close()
        self._pool = None
        self._checkpointer = None

    def get(self):
        if settings.checkpoint_enabled and self._checkpointer is None:
            raise RuntimeError("LangGraph checkpoint尚未完成初始化")
        return self._checkpointer

    def _backend(self) -> str:
        return (settings.checkpoint_backend or "").strip().lower()


checkpoint_manager = CheckpointManager()
