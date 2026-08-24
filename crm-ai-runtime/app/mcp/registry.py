import json
import logging
import re
from dataclasses import dataclass
from typing import Any

from langchain_core.tools import BaseTool
from langchain_mcp_adapters.client import MultiServerMCPClient

from app.agents.models import AgentDefinition, McpServerDefinition
from app.agents.repository import agent_repository
from app.core.config import settings
from app.schemas.runtime import RuntimeRunRequest

logger = logging.getLogger(__name__)


@dataclass
class McpToolBundle:
    client: MultiServerMCPClient | None
    tools: list[BaseTool]


class McpRegistry:
    async def resolve(self, request: RuntimeRunRequest, agent: AgentDefinition) -> list[McpServerDefinition]:
        return await agent_repository.resolve_mcps(request, agent)

    async def load_tools(self, definitions: list[McpServerDefinition]) -> McpToolBundle:
        if not definitions:
            return McpToolBundle(client=None, tools=[])
        connections: dict[str, dict[str, Any]] = {}
        for index, definition in enumerate(definitions):
            server_name = self._server_name(definition, index)
            connections[server_name] = self._connection(definition)
        client = MultiServerMCPClient(
            connections,
            tool_name_prefix=True,
            handle_tool_errors=True,
        )
        try:
            tools = await client.get_tools()
        except Exception:
            if settings.mcp_fail_fast:
                raise
            logger.exception("MCP工具加载失败，本次运行不挂载MCP工具")
            return McpToolBundle(client=None, tools=[])
        logger.info("MCP工具加载完成 serverCount=%s toolCount=%s", len(connections), len(tools))
        return McpToolBundle(client=client, tools=tools)

    def _connection(self, definition: McpServerDefinition) -> dict[str, Any]:
        transport = (definition.transport_type or "").strip().upper()
        if transport == "STDIO":
            command = (definition.command or "").strip()
            if not command:
                raise ValueError("MCP Stdio服务未配置启动命令：%s" % (definition.name or definition.id))
            return {
                "transport": "stdio",
                "command": command,
                "args": self._arguments(definition.arguments_json),
            }
        endpoint = (definition.endpoint or "").strip()
        if not endpoint:
            raise ValueError("MCP服务未配置访问地址：%s" % (definition.name or definition.id))
        if transport == "SSE":
            connection: dict[str, Any] = {"transport": "sse", "url": endpoint}
        elif transport in {"STREAMABLE_HTTP", "HTTP"}:
            connection = {"transport": "http", "url": endpoint}
        else:
            raise ValueError("不支持的MCP传输类型：%s" % transport)
        headers = self._json_object(definition.headers_json)
        if headers:
            connection["headers"] = headers
        return connection

    def _arguments(self, value: str | None) -> list[str]:
        text = (value or "").strip()
        if not text:
            return []
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as ex:
            raise ValueError("MCP启动参数必须是JSON数组") from ex
        if not isinstance(parsed, list):
            raise ValueError("MCP启动参数必须是JSON数组")
        return [str(item) for item in parsed]

    def _json_object(self, value: str | None) -> dict[str, str]:
        text = (value or "").strip()
        if not text:
            return {}
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as ex:
            raise ValueError("MCP请求头必须是JSON对象") from ex
        if not isinstance(parsed, dict):
            raise ValueError("MCP请求头必须是JSON对象")
        return {str(key): str(item) for key, item in parsed.items() if item is not None}

    def _server_name(self, definition: McpServerDefinition, index: int) -> str:
        source = str(definition.id or definition.code or definition.name or index)
        value = re.sub(r"[^a-zA-Z0-9_-]+", "_", source).strip("_").lower()
        return "mcp_" + (value or str(index))


mcp_registry = McpRegistry()
