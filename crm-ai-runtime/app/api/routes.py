from fastapi import APIRouter, Depends, HTTPException

from app.core.security import require_internal_token
from app.graphs.registry import graph_registry
from app.schemas.runtime import RuntimeRunRequest, RuntimeRunResponse

router = APIRouter()


@router.get("/health")
async def health():
    return {"status": "UP"}


@router.post("/internal/ai/runtime/run", response_model=RuntimeRunResponse)
async def run_runtime(
    request: RuntimeRunRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await graph_registry.run(request)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex
    except RuntimeError as ex:
        raise HTTPException(status_code=500, detail=str(ex)) from ex


@router.post("/internal/ai/lead/analyze", response_model=RuntimeRunResponse)
async def analyze_lead(
    request: RuntimeRunRequest,
    _: None = Depends(require_internal_token),
):
    request.scene_code = "LEAD_ANALYZE"
    return await run_runtime(request, None)
