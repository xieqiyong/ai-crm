import base64
import hashlib
import hmac
import json
import time
from dataclasses import dataclass, field
from typing import Any

from fastapi import HTTPException, Request

from app.core.config import settings


@dataclass
class CurrentPrincipal:
    tenant_id: str
    user_id: str
    username: str = ""
    display_name: str = ""
    session_id: str = ""
    data_scope: str = "SELF"
    permissions: list[str] = field(default_factory=list)
    menu_permissions: list[str] = field(default_factory=list)
    data_permissions: list[str] = field(default_factory=list)


def require_current_principal(request: Request) -> CurrentPrincipal:
    token = _bearer_token(request)
    if not token:
        raise HTTPException(status_code=401, detail="登录已失效，请重新登录")
    try:
        claims = _parse_jwt(token)
    except ValueError as ex:
        raise HTTPException(status_code=401, detail=str(ex)) from ex
    tenant_id = _text(claims.get("tenantId"))
    user_id = _text(claims.get("sub"))
    if not tenant_id or not user_id:
        raise HTTPException(status_code=401, detail="登录身份不完整")
    return CurrentPrincipal(
        tenant_id=tenant_id,
        user_id=user_id,
        username=_text(claims.get("username")),
        display_name=_text(claims.get("displayName")),
        session_id=_text(claims.get("sessionId")),
        data_scope=_text(claims.get("dataScope")) or "SELF",
        permissions=_string_list(claims.get("permissions")),
        menu_permissions=_string_list(claims.get("menuPermissions")),
        data_permissions=_string_list(claims.get("dataPermissions")),
    )


def require_any_authority(principal: CurrentPrincipal, *authorities: str) -> None:
    permissions = set(principal.permissions or [])
    if "*" in permissions:
        return
    for authority in authorities:
        if authority in permissions:
            return
    raise HTTPException(status_code=403, detail="无权访问该功能")


def _parse_jwt(token: str) -> dict[str, Any]:
    if not settings.jwt_secret:
        raise ValueError("Python AI Runtime未配置JWT密钥")
    parts = token.split(".")
    if len(parts) != 3:
        raise ValueError("登录凭证格式不正确")
    header = _json_part(parts[0])
    payload = _json_part(parts[1])
    alg = _text(header.get("alg")).upper()
    if alg not in {"HS256", "HS384", "HS512"}:
        raise ValueError("登录凭证签名算法不支持")
    expected = _sign(parts[0] + "." + parts[1], alg)
    if not hmac.compare_digest(expected, parts[2]):
        raise ValueError("登录凭证签名无效")
    exp = payload.get("exp")
    if exp is not None and int(exp) < int(time.time()):
        raise ValueError("登录已失效，请重新登录")
    return payload


def _sign(value: str, alg: str) -> str:
    digest = hashlib.sha256
    if alg == "HS384":
        digest = hashlib.sha384
    if alg == "HS512":
        digest = hashlib.sha512
    signature = hmac.new(settings.jwt_secret.encode("utf-8"), value.encode("utf-8"), digest).digest()
    return _base64url_encode(signature)


def _json_part(value: str) -> dict[str, Any]:
    try:
        decoded = _base64url_decode(value).decode("utf-8")
        parsed = json.loads(decoded)
    except Exception as ex:
        raise ValueError("登录凭证解析失败") from ex
    if not isinstance(parsed, dict):
        raise ValueError("登录凭证内容不正确")
    return parsed


def _bearer_token(request: Request) -> str:
    header = request.headers.get("Authorization") or ""
    if header.lower().startswith("bearer "):
        return header[7:].strip()
    return ""


def _base64url_decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode((value + padding).encode("utf-8"))


def _base64url_encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("utf-8").rstrip("=")


def _string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    result: list[str] = []
    for item in value:
        if item is not None:
            result.append(str(item))
    return result


def _text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()
