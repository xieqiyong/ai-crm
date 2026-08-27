from typing import Any

import httpx

from app.core.config import settings


class CrmApiClient:
    async def post(
            self,
            path: str,
            payload: dict[str, Any],
            authorization: str | None,
            trace_id: str | None = None) -> Any:
        if not authorization:
            raise ValueError("当前智能体请求缺少用户授权，不能访问CRM业务数据")
        base_url = settings.crm_api_base_url.rstrip("/")
        url = base_url + "/" + path.lstrip("/")
        headers = {
            "Authorization": authorization,
            "Content-Type": "application/json",
        }
        if trace_id:
            headers["X-Trace-Id"] = trace_id
        timeout = max(int(settings.crm_api_timeout_seconds or 30), 5)
        try:
            async with httpx.AsyncClient(timeout=timeout, trust_env=False) as client:
                response = await client.post(url, json=payload, headers=headers)
        except httpx.HTTPError as ex:
            message = str(ex).strip() or type(ex).__name__
            raise RuntimeError("CRM接口调用失败，地址：%s，原因：%s" % (url, message)) from ex
        if response.status_code < 200 or response.status_code >= 300:
            raise RuntimeError("CRM接口调用失败，状态码：%s" % response.status_code)
        try:
            body = response.json()
        except ValueError as ex:
            raise RuntimeError("CRM接口未返回合法JSON") from ex
        if not isinstance(body, dict):
            return body
        if body.get("success") is False:
            raise RuntimeError(str(body.get("message") or "CRM接口处理失败"))
        return body.get("data") if "data" in body else body


crm_api_client = CrmApiClient()
