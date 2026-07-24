from app.graphs.lead_analyze import build_graph
from app.schemas.runtime import RuntimeRunRequest, RuntimeRunResponse


class GraphRegistry:
    def __init__(self):
        self._graphs = {
            "LEAD_ANALYZE": build_graph(),
        }

    async def run(self, request: RuntimeRunRequest) -> RuntimeRunResponse:
        scene_code = (request.scene_code or "").strip().upper()
        graph = self._graphs.get(scene_code)
        if graph is None:
            raise ValueError(f"暂不支持的AI场景：{scene_code or '未指定'}")
        state = await graph.ainvoke({"request": request, "events": []})
        events = state.get("events") or []
        output = state.get("output") or ""
        return RuntimeRunResponse(success=True, output=output, events=events)


graph_registry = GraphRegistry()
