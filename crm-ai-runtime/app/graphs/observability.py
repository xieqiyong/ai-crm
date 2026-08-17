import logging
import time
from collections.abc import Awaitable, Callable
from typing import Any

from app.core.trace_utils import trace_node_inputs, trace_node_outputs, traceable
from app.runtime.streaming import emit_runtime_status
from app.services.event_bus import runtime_event


logger = logging.getLogger("crm_ai_runtime.graph.node")


NodeHandler = Callable[[dict[str, Any]], Awaitable[dict[str, Any]]]


def observed_node(
        node: str,
        name: str,
        handler: NodeHandler,
        run_type: str = "chain",
) -> NodeHandler:
    traced_handler = traceable(
        run_type=run_type,
        name=name,
        process_inputs=trace_node_inputs,
        process_outputs=trace_node_outputs,
    )(handler)

    async def wrapper(state: dict[str, Any]) -> dict[str, Any]:
        started_at = time.perf_counter()
        await emit_runtime_status(
            "开始：" + name,
            {"node": node, "nodeName": name, "status": "RUNNING"},
        )
        try:
            result = await traced_handler(state)
            elapsed_ms = elapsed(started_at)
            result = ensure_result(result)
            result["events"] = enrich_events(result.get("events"), node, name, elapsed_ms, "SUCCESS")
            await emit_runtime_status(
                "完成：" + name,
                {"node": node, "nodeName": name, "status": "SUCCESS", "elapsedMs": elapsed_ms},
            )
            logger.info("Graph节点完成 node=%s name=%s elapsedMs=%s", node, name, elapsed_ms)
            return result
        except Exception:
            elapsed_ms = elapsed(started_at)
            await emit_runtime_status(
                "异常：" + name,
                {"node": node, "nodeName": name, "status": "FAILED", "elapsedMs": elapsed_ms},
            )
            logger.exception("Graph节点异常 node=%s name=%s elapsedMs=%s", node, name, elapsed_ms)
            raise

    return wrapper


def ensure_result(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    return {}


def enrich_events(events: Any, node: str, name: str, elapsed_ms: int, status: str) -> list[dict[str, Any]]:
    values = events if isinstance(events, list) else []
    if not values:
        values = [runtime_event("STEP", metadata={})]
    for event in values:
        if not isinstance(event, dict):
            continue
        metadata = event.setdefault("metadata", {})
        metadata.setdefault("node", node)
        metadata.setdefault("status", status)
        metadata["nodeName"] = metadata.get("nodeName") or name
        metadata["elapsedMs"] = elapsed_ms
    return values


def elapsed(started_at: float) -> int:
    return int((time.perf_counter() - started_at) * 1000)
