from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, Request

from app.core.security import require_internal_token
from app.core.trace_utils import runtime_trace_metadata, runtime_trace_tags
from app.graphs.registry import graph_registry
from app.schemas.runtime import RuntimeRunRequest, RuntimeRunResponse

router = APIRouter()


@router.post("/health")
async def health():
    return {"status": "UP"}


@router.post("/internal/ai/runtime/run", response_model=RuntimeRunResponse)
async def run_runtime(
    runtime_request: RuntimeRunRequest,
    http_request: Request,
    _: None = Depends(require_internal_token),
):
    enrich_trace_context(runtime_request, http_request)
    return await execute_runtime(runtime_request)


@router.post("/internal/ai/lead/analyze", response_model=RuntimeRunResponse)
async def analyze_lead(
    runtime_request: RuntimeRunRequest,
    http_request: Request,
    _: None = Depends(require_internal_token),
):
    enrich_trace_context(runtime_request, http_request)
    runtime_request.scene_code = "LEAD_ANALYZE"
    return await execute_runtime(runtime_request)


async def execute_runtime(request: RuntimeRunRequest) -> RuntimeRunResponse:
    try:
        return await graph_registry.run(
            request,
            langsmith_extra={
                "metadata": runtime_trace_metadata(request),
                "tags": runtime_trace_tags(request),
            },
        )
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex
    except RuntimeError as ex:
        raise HTTPException(status_code=500, detail=str(ex)) from ex


def enrich_trace_context(runtime_request: RuntimeRunRequest, http_request: Request) -> None:
    if not runtime_request.run_id:
        runtime_request.run_id = uuid4().hex
    trace_id = http_request.headers.get("X-Trace-Id")
    if trace_id:
        runtime_request.context["traceId"] = trace_id
