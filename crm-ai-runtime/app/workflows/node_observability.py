"""Workflow节点可观测封装。"""

import logging
import time
from functools import wraps

from langgraph.config import get_stream_writer

logger = logging.getLogger("crm_ai_runtime.graph.node")


def observable_node(node_name: str, display_name: str):
    def decorator(func):
        @wraps(func)
        async def wrapper(state, runtime):
            started_at = time.perf_counter()
            writer = get_stream_writer()
            writer({
                "type": "GRAPH_NODE_STATUS",
                "content": display_name,
                "node": node_name,
                "nodeName": display_name,
                "status": "RUNNING",
            })
            logger.info("Graph节点开始 node=%s name=%s", node_name, display_name)
            try:
                result = await func(state, runtime)
                elapsed_ms = _elapsed_ms(started_at)
                writer({
                    "type": "GRAPH_NODE_STATUS",
                    "content": display_name,
                    "node": node_name,
                    "nodeName": display_name,
                    "status": "SUCCESS",
                    "elapsedMs": elapsed_ms,
                })
                logger.info(
                    "Graph节点完成 node=%s name=%s elapsedMs=%s",
                    node_name,
                    display_name,
                    elapsed_ms,
                )
                return result
            except Exception:
                elapsed_ms = _elapsed_ms(started_at)
                writer({
                    "type": "GRAPH_NODE_STATUS",
                    "content": display_name + "失败",
                    "node": node_name,
                    "nodeName": display_name,
                    "status": "FAILED",
                    "elapsedMs": elapsed_ms,
                })
                logger.exception(
                    "Graph节点异常 node=%s name=%s elapsedMs=%s",
                    node_name,
                    display_name,
                    elapsed_ms,
                )
                raise

        return wrapper

    return decorator


def _elapsed_ms(started_at: float) -> float:
    return round((time.perf_counter() - started_at) * 1000, 3)
