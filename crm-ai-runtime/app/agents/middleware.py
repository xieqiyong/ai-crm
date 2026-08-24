import logging
import time

from langchain.agents.middleware import AgentMiddleware, ModelRequest, ModelResponse, ToolCallRequest
from langchain.messages import AIMessage, ToolMessage
from langgraph.types import Command

from app.runtime.execution_context import AgentExecutionContext

model_logger = logging.getLogger("crm_ai_runtime.agent.model")
tool_logger = logging.getLogger("crm_ai_runtime.agent.tool")


class AgentObservabilityMiddleware(AgentMiddleware):
    async def awrap_model_call(
            self,
            request: ModelRequest[AgentExecutionContext],
            handler) -> ModelResponse | AIMessage:
        started_at = time.perf_counter()
        context = request.runtime.context if request.runtime else None
        model_logger.info(
            "大模型调用开始 sceneCode=%s messageCount=%s toolCount=%s",
            context.scene_code if context else "",
            len(request.messages or []),
            len(request.tools or []),
        )
        try:
            response = await handler(request)
            input_tokens, output_tokens, total_tokens = self._response_usage(response)
            model_logger.info(
                "大模型调用完成 sceneCode=%s elapsedMs=%s inputTokens=%s outputTokens=%s totalTokens=%s",
                context.scene_code if context else "",
                self._elapsed(started_at),
                input_tokens,
                output_tokens,
                total_tokens,
            )
            return response
        except Exception:
            model_logger.exception(
                "大模型调用异常 sceneCode=%s elapsedMs=%s",
                context.scene_code if context else "",
                self._elapsed(started_at),
            )
            raise

    async def awrap_tool_call(
            self,
            request: ToolCallRequest,
            handler) -> ToolMessage | Command:
        started_at = time.perf_counter()
        tool_name = str(request.tool_call.get("name") or "unknown")
        argument_names = sorted((request.tool_call.get("args") or {}).keys())
        tool_logger.info("工具调用开始 tool=%s argumentNames=%s", tool_name, argument_names)
        try:
            response = await handler(request)
            tool_logger.info("工具调用完成 tool=%s elapsedMs=%s", tool_name, self._elapsed(started_at))
            return response
        except Exception:
            tool_logger.exception("工具调用异常 tool=%s elapsedMs=%s", tool_name, self._elapsed(started_at))
            raise

    def _response_usage(self, response) -> tuple[int, int, int]:
        messages = []
        if isinstance(response, AIMessage):
            messages = [response]
        elif hasattr(response, "result"):
            messages = list(response.result or [])
        input_tokens = 0
        output_tokens = 0
        total_tokens = 0
        for message in messages:
            usage = getattr(message, "usage_metadata", None) or {}
            input_tokens += int(usage.get("input_tokens") or 0)
            output_tokens += int(usage.get("output_tokens") or 0)
            total_tokens += int(usage.get("total_tokens") or 0)
        return input_tokens, output_tokens, total_tokens or input_tokens + output_tokens

    def _elapsed(self, started_at: float) -> int:
        return int((time.perf_counter() - started_at) * 1000)
