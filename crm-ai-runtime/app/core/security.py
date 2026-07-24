from typing import Annotated

from fastapi import Header, HTTPException

from app.core.config import settings


async def require_internal_token(
    x_internal_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None,
) -> None:
    if not settings.internal_token:
        return
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="内部服务令牌无效")
