import asyncio
from typing import Any

from langchain.agents.middleware.model_call_limit import ModelCallLimitExceededError

from app.core.credentials import runtime_credential_store
from app.core.trace_utils import trace_enabled, trace_runtime_inputs, trace_runtime_outputs, traceable
from app.persistence.checkpoint import checkpoint_manager
from app.platform.id_generator import id_generator
from app.runtime.context import runtime_context_builder
from app.runtime.scene_dispatcher import scene_dispatcher
from app.runtime.store import runtime_store
from app.runtime.stream_adapter import AgentStreamAccumulator
from app.schemas.runtime import RuntimeRunRequest, RuntimeRunResponse


class AgentRuntimeExecutor:
    @traceable(
        run_type="chain",
        name="AI场景运行",
        process_inputs=trace_runtime_inputs,
        process_outputs=trace_runtime_outputs,
    )
    async def run(
            self,
            request: RuntimeRunRequest,
            langsmith_extra: dict | None = None) -> RuntimeRunResponse:
        self._complete_runtime_identity(request)
        self._validate_checkpoint_backend()
        runtime_context = await runtime_context_builder.build(request)
        started = False
        try:
            await runtime_store.start_run(request, runtime_context.agent)
            started = True
            runtime_credential_store.register(request.run_id, request.authorization)
            checkpointer = checkpoint_manager.get()
            bundle = await scene_dispatcher.build(request, runtime_context, checkpointer)
            accumulator = await self._stream_graph(
                request,
                bundle,
                langsmith_extra or {},
            )
            output = accumulator.output()
            usage = accumulator.usage()
            events = accumulator.finish_events(output)
            await runtime_store.finish_run(request, output, events, usage)
            return RuntimeRunResponse(
                success=True,
                output=output,
                events=events,
                run_id=request.run_id,
                conversation_id=request.conversation_id,
                thread_id=self._thread_id(request),
                checkpoint_enabled=checkpointer is not None,
                trace_enabled=trace_enabled(),
                trace_id=self._trace_id(request),
            )
        except asyncio.CancelledError:
            if started:
                await runtime_store.cancel_run(request)
            raise
        except ModelCallLimitExceededError as ex:
            message = "智能体已达到本次大模型最大调用轮次，请缩小任务范围或调整最大调用轮次"
            if started:
                await runtime_store.fail_run(request, message)
            raise RuntimeError(message) from ex
        except Exception as ex:
            message = str(ex).strip() or type(ex).__name__
            if started:
                await runtime_store.fail_run(request, message)
            raise
        finally:
            runtime_credential_store.revoke(request.run_id)

    async def _stream_graph(
            self,
            request: RuntimeRunRequest,
            bundle,
            langsmith_extra: dict[str, Any]) -> AgentStreamAccumulator:
        accumulator = AgentStreamAccumulator()
        async for part in bundle.graph.astream(
                bundle.input_state,
                config=self._runtime_config(request, langsmith_extra),
                context=bundle.execution_context,
                stream_mode=["messages", "updates", "values", "custom"],
                version="v2"):
            await accumulator.consume(part)
        return accumulator

    def _runtime_config(self, request: RuntimeRunRequest, langsmith_extra: dict[str, Any]) -> dict[str, Any]:
        max_iters = request.agent.max_iters if request.agent and request.agent.max_iters else 8
        config: dict[str, Any] = {
            "configurable": {"thread_id": self._thread_id(request)},
            "recursion_limit": max(10, min(int(max_iters), 50) * 3 + 10),
        }
        metadata = langsmith_extra.get("metadata")
        tags = langsmith_extra.get("tags")
        if isinstance(metadata, dict):
            config["metadata"] = metadata
        if isinstance(tags, list):
            config["tags"] = tags
        return config

    def _thread_id(self, request: RuntimeRunRequest) -> str:
        thread_value = request.conversation_id or request.run_id
        values = [
            self._safe_part(request.scene_code),
            self._safe_part(request.tenant_id),
            self._safe_part(request.user_id),
            self._safe_part(thread_value),
        ]
        return ":".join(item for item in values if item)

    def _complete_runtime_identity(self, request: RuntimeRunRequest) -> None:
        if not request.run_id:
            request.run_id = str(id_generator.next_id())

    def _validate_checkpoint_backend(self) -> None:
        checkpoint_manager.get()

    def _trace_id(self, request: RuntimeRunRequest) -> str | None:
        value = request.context.get("traceId") or request.context.get("trace_id")
        return str(value) if value else None

    def _safe_part(self, value: Any) -> str:
        return str(value).strip().replace(":", "_") if value is not None else ""


agent_runtime_executor = AgentRuntimeExecutor()
