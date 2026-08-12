from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.routes import router
from app.core.config import settings
from app.platform.nacos import nacos_naming_client


@asynccontextmanager
async def lifespan(app: FastAPI):
    await nacos_naming_client.start()
    try:
        yield
    finally:
        await nacos_naming_client.stop()


app = FastAPI(title=settings.app_name, lifespan=lifespan)
app.include_router(router)
