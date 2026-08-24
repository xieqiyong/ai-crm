from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.routes import router
from app.core.config import settings
from app.core.logging_config import configure_logging
from app.core.request_logging import RequestLoggingMiddleware
from app.persistence.checkpoint import checkpoint_manager
from app.platform.nacos import nacos_naming_client

configure_logging()


@asynccontextmanager
async def lifespan(app: FastAPI):
    await checkpoint_manager.start()
    try:
        await nacos_naming_client.start()
        yield
    finally:
        await nacos_naming_client.stop()
        await checkpoint_manager.stop()


app = FastAPI(title=settings.app_name, lifespan=lifespan)
app.add_middleware(RequestLoggingMiddleware)
app.include_router(router)
