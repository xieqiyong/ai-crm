import asyncio

from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

from app.core.config import settings


async def setup() -> None:
    if not settings.checkpoint_postgres_uri.strip():
        raise RuntimeError("PostgreSQL checkpoint地址未配置")
    async with AsyncPostgresSaver.from_conn_string(settings.checkpoint_postgres_uri) as checkpointer:
        await checkpointer.setup()
    print("LangGraph checkpoint表初始化完成")


if __name__ == "__main__":
    asyncio.run(setup())
