import asyncio
import json
import logging
import socket
from typing import Any

import httpx

from app.core.config import settings

logger = logging.getLogger(__name__)


class NacosNamingClient:
    def __init__(self):
        self._client: httpx.AsyncClient | None = None
        self._access_token = ""
        self._heartbeat_task: asyncio.Task | None = None
        self._registered = False

    async def start(self) -> None:
        if not settings.nacos_enabled:
            return
        self._client = httpx.AsyncClient(base_url=self._base_url(), timeout=10)
        await self._login()
        await self._register()
        if settings.nacos_ephemeral:
            self._heartbeat_task = asyncio.create_task(self._heartbeat_loop())

    async def stop(self) -> None:
        if self._heartbeat_task:
            self._heartbeat_task.cancel()
            try:
                await self._heartbeat_task
            except asyncio.CancelledError:
                pass
        if self._registered:
            await self._unregister()
        if self._client:
            await self._client.aclose()

    async def _login(self) -> None:
        if not settings.nacos_username.strip():
            return
        response = await self._client.post(
            "/nacos/v1/auth/users/login",
            data={
                "username": settings.nacos_username,
                "password": settings.nacos_password,
            },
        )
        response.raise_for_status()
        data = response.json()
        self._access_token = str(data.get("accessToken") or "")
        if not self._access_token:
            raise RuntimeError("Nacos登录失败，未返回访问令牌")

    async def _register(self) -> None:
        response = await self._client.post("/nacos/v1/ns/instance", data=self._common_payload())
        response.raise_for_status()
        if response.text.strip().lower() != "ok":
            raise RuntimeError("Nacos服务注册失败：" + response.text)
        self._registered = True
        logger.info("Nacos服务注册完成，服务名：%s，地址：%s:%s", settings.nacos_service_name, self._ip(), self._port())

    async def _unregister(self) -> None:
        try:
            response = await self._client.delete("/nacos/v1/ns/instance", params=self._common_payload())
            response.raise_for_status()
            logger.info("Nacos服务注销完成，服务名：%s", settings.nacos_service_name)
        except Exception as ex:
            logger.warning("Nacos服务注销失败，原因：%s", ex)

    async def _heartbeat_loop(self) -> None:
        while True:
            await asyncio.sleep(max(settings.nacos_heartbeat_seconds, 1))
            try:
                await self._heartbeat()
            except Exception as ex:
                logger.warning("Nacos心跳发送失败，原因：%s", ex)

    async def _heartbeat(self) -> None:
        beat = {
            "serviceName": settings.nacos_service_name,
            "ip": self._ip(),
            "port": self._port(),
            "cluster": settings.nacos_cluster_name,
            "weight": 1.0,
            "metadata": self._metadata(),
        }
        payload = self._common_payload()
        payload["beat"] = json.dumps(beat, ensure_ascii=False)
        response = await self._client.put("/nacos/v1/ns/instance/beat", data=payload)
        response.raise_for_status()

    def _common_payload(self) -> dict[str, Any]:
        payload = {
            "serviceName": settings.nacos_service_name,
            "groupName": settings.nacos_group,
            "ip": self._ip(),
            "port": self._port(),
            "clusterName": settings.nacos_cluster_name,
            "ephemeral": str(settings.nacos_ephemeral).lower(),
            "metadata": json.dumps(self._metadata(), ensure_ascii=False),
        }
        if settings.nacos_namespace.strip():
            payload["namespaceId"] = settings.nacos_namespace.strip()
        if self._access_token:
            payload["accessToken"] = self._access_token
        return payload

    def _metadata(self) -> dict[str, str]:
        return {
            "scheme": "http",
            "runtime": "langgraph",
            "app": settings.app_name,
        }

    def _base_url(self) -> str:
        value = settings.nacos_server_addr.strip()
        if value.startswith("http://") or value.startswith("https://"):
            return value.rstrip("/")
        return "http://" + value.rstrip("/")

    def _ip(self) -> str:
        if settings.nacos_ip.strip():
            return settings.nacos_ip.strip()
        return socket.gethostbyname(socket.gethostname())

    def _port(self) -> int:
        return settings.nacos_port or settings.port


nacos_naming_client = NacosNamingClient()
