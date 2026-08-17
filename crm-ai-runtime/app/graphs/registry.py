from app.core.config import settings
from app.core.trace_utils import trace_enabled, trace_runtime_inputs, trace_runtime_outputs, traceable
from app.graphs.generic_assistant import build_graph as build_generic_graph
from app.graphs.lead_analyze import build_graph as build_lead_graph
from app.platform.id_generator import id_generator
from app.runtime.context import runtime_context_builder
from app.runtime.store import runtime_store
from app.schemas.runtime import RuntimeRunRequest, RuntimeRunResponse

try:
    from langgraph.checkpoint.memory import InMemorySaver
except ImportError:
    from langgraph.checkpoint.memory import MemorySaver as InMemorySaver


class GraphRegistry:
    def __init__(self):
        self._postgres_setup_done = False
        self._memory_checkpointer = self._build_memory_checkpointer()
        self._graphs = self._build_graphs(self._memory_checkpointer)

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
        if self._postgres_checkpoint_enabled():
            return await self._run_with_postgres(request)
        graph = self._resolve_graph(request)
        return await self._run_graph(request, graph)

    def _build_memory_checkpointer(self):
        if not settings.checkpoint_enabled:
            return None
        if self._checkpoint_backend() != "memory":
            return None
        return InMemorySaver()

    def _build_graphs(self, checkpointer=None):
        generic = build_generic_graph(checkpointer=checkpointer)
        return {
            "GENERAL_ASSISTANT": generic,
            "CUSTOMER_DEEP_SUMMARY": generic,
            "CHANNEL_ANALYZE": generic,
            "LEAD_ANALYZE": build_lead_graph(checkpointer=checkpointer),
        }

    async def _run_with_postgres(self, request: RuntimeRunRequest) -> RuntimeRunResponse:
        if not settings.checkpoint_postgres_uri.strip():
            raise RuntimeError("PostgreSQL checkpoint 地址未配置")
        try:
            from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        except ImportError as ex:
            raise RuntimeError("PostgreSQL checkpoint 依赖未安装") from ex
        async with AsyncPostgresSaver.from_conn_string(settings.checkpoint_postgres_uri) as checkpointer:
            if settings.checkpoint_auto_setup and not self._postgres_setup_done:
                await checkpointer.setup()
                self._postgres_setup_done = True
            graph = self._resolve_graph(request, build_generic_graph(checkpointer=checkpointer))
            return await self._run_graph(request, graph)

    async def _run_graph(self, request: RuntimeRunRequest, graph) -> RuntimeRunResponse:
        runtime_context = await runtime_context_builder.build(request)
        await runtime_store.start_run(request, runtime_context.agent)
        try:
            state = await graph.ainvoke(
                self._input_state(request, runtime_context.to_state()),
                config=self._runtime_config(request),
            )
            events = state.get("events") or []
            output = state.get("output") or ""
            usage = state.get("usage") or {}
            await runtime_store.finish_run(request, output, events, usage)
            return RuntimeRunResponse(
                success=True,
                output=output,
                events=events,
                run_id=request.run_id,
                conversation_id=request.conversation_id,
                thread_id=self._thread_id(request),
                checkpoint_enabled=settings.checkpoint_enabled,
                trace_enabled=trace_enabled(),
                trace_id=self._trace_id(request),
            )
        except Exception as ex:
            await runtime_store.fail_run(request, str(ex))
            raise

    def _resolve_graph(self, request: RuntimeRunRequest, fallback=None):
        scene_code = (request.scene_code or "").strip().upper()
        graph = self._graphs.get(scene_code)
        if graph is None:
            return fallback or self._graphs.get("GENERAL_ASSISTANT")
        return graph

    def _input_state(self, request: RuntimeRunRequest, runtime_context: dict) -> dict:
        return {
            "request": request.model_dump(by_alias=True, mode="json"),
            "runtime": runtime_context,
            "events": [],
        }

    def _runtime_config(self, request: RuntimeRunRequest) -> dict:
        return {
            "configurable": {
                "thread_id": self._thread_id(request),
            }
        }

    def _thread_id(self, request: RuntimeRunRequest) -> str:
        thread_value = request.conversation_id or request.run_id
        values = [
            self._safe_part(request.scene_code),
            self._safe_part(request.tenant_id),
            self._safe_part(request.user_id),
            self._safe_part(thread_value),
        ]
        return ":".join([item for item in values if item])

    def _complete_runtime_identity(self, request: RuntimeRunRequest) -> None:
        if not request.run_id:
            request.run_id = str(id_generator.next_id())

    def _postgres_checkpoint_enabled(self) -> bool:
        if not settings.checkpoint_enabled:
            return False
        return self._checkpoint_backend() == "postgres"

    def _checkpoint_backend(self) -> str:
        return (settings.checkpoint_backend or "").strip().lower()

    def _validate_checkpoint_backend(self) -> None:
        if not settings.checkpoint_enabled:
            return
        if self._checkpoint_backend() in {"memory", "postgres"}:
            return
        raise RuntimeError("暂不支持的 checkpoint 类型：%s" % self._checkpoint_backend())

    def _trace_id(self, request: RuntimeRunRequest) -> str | None:
        value = request.context.get("traceId") or request.context.get("trace_id")
        if value is None:
            return None
        return str(value)

    def _safe_part(self, value) -> str:
        if value is None:
            return ""
        return str(value).strip().replace(":", "_")


graph_registry = GraphRegistry()
