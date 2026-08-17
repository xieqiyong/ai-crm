import logging
import time
import uuid

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware


logger = logging.getLogger("crm_ai_runtime.request")


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        start_time = time.perf_counter()
        trace_id = request.headers.get("X-Trace-Id") or request.headers.get("X-Request-Id") or uuid.uuid4().hex
        method = request.method
        path = request.url.path
        client_host = request.client.host if request.client else "-"
        logger.info("接口请求开始 traceId=%s method=%s path=%s client=%s", trace_id, method, path, client_host)
        try:
            response = await call_next(request)
            elapsed_ms = int((time.perf_counter() - start_time) * 1000)
            response.headers["X-Trace-Id"] = trace_id
            logger.info(
                "接口请求完成 traceId=%s method=%s path=%s status=%s elapsedMs=%s",
                trace_id,
                method,
                path,
                response.status_code,
                elapsed_ms,
            )
            return response
        except Exception:
            elapsed_ms = int((time.perf_counter() - start_time) * 1000)
            logger.exception("接口请求异常 traceId=%s method=%s path=%s elapsedMs=%s", trace_id, method, path, elapsed_ms)
            raise
