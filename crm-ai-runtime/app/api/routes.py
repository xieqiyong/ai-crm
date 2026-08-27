import asyncio
import json
import logging
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse

from app.core.auth import CurrentPrincipal, require_any_authority, require_current_principal
from app.core.config import settings
from app.core.security import require_internal_token
from app.core.trace_utils import runtime_trace_metadata, runtime_trace_tags
from app.runtime.agent_management_repository import agent_management_repository
from app.runtime.assistant_repository import assistant_repository
from app.runtime.business_repository import business_repository
from app.runtime.executor import agent_runtime_executor
from app.runtime.streaming import STREAM_END, RuntimeStream, reset_runtime_stream, set_runtime_stream
from app.schemas.runtime import (
    AgentIdManageRequest,
    AgentManageRequest,
    AgentMcpSaveManageRequest,
    AgentSaveManageRequest,
    AgentSkillSaveManageRequest,
    AgentTokenQuotaAssignManageRequest,
    AgentTokenQuotaClearManageRequest,
    AssistantAgentListRequest,
    AssistantConversationActionRequest,
    AssistantConversationListRequest,
    AssistantMessagesRequest,
    AssistantRunRequest,
    AssistantStopRequest,
    RuntimeAgent,
    RuntimeRunRequest,
    RuntimeRunResponse,
)

router = APIRouter()
active_tasks: dict[str, asyncio.Task] = {}
logger = logging.getLogger(__name__)


def api_ok(data=None) -> dict:
    return {
        "success": True,
        "code": "0",
        "message": "处理成功",
        "data": data,
    }


def api_fail(code: str, message: str) -> dict:
    return {
        "success": False,
        "code": code,
        "message": message,
        "data": None,
    }


async def json_body(http_request: Request) -> dict:
    try:
        value = await http_request.json()
    except Exception:
        return {}
    return value if isinstance(value, dict) else {}


def with_principal(payload: dict, principal: CurrentPrincipal) -> dict:
    value = dict(payload or {})
    value["tenantId"] = principal.tenant_id
    value["userId"] = principal.user_id
    value["dataScope"] = principal.data_scope
    value["permissions"] = principal.permissions
    return value


@router.post("/health")
async def health():
    return {"status": "UP"}


@router.post("/api/agent/page")
async def public_agent_page(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:view", "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.page(
            principal.tenant_id,
            int(payload.get("pageNo") or 1),
            int(payload.get("pageSize") or 20),
        ))
    except ValueError as ex:
        return api_fail("AI_AGENT_001", str(ex))


@router.post("/api/agent/detail")
async def public_agent_detail(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:view", "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.detail(principal.tenant_id, payload.get("id")))
    except ValueError as ex:
        return api_fail("AI_AGENT_002", str(ex))


@router.post("/api/agent/save")
async def public_agent_save(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.save_agent(principal.tenant_id, payload))
    except ValueError as ex:
        return api_fail("AI_AGENT_003", str(ex))


@router.post("/api/agent/mcp/list")
async def public_agent_mcp_list(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:view", "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.mcps(principal.tenant_id, payload.get("agentId")))
    except ValueError as ex:
        return api_fail("AI_AGENT_MCP_001", str(ex))


@router.post("/api/agent/mcp/save")
async def public_agent_mcp_save(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.save_mcp(principal.tenant_id, payload))
    except ValueError as ex:
        return api_fail("AI_AGENT_MCP_002", str(ex))


@router.post("/api/agent/mcp/delete")
async def public_agent_mcp_delete(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.delete_mcp(principal.tenant_id, payload.get("id")))
    except ValueError as ex:
        return api_fail("AI_AGENT_MCP_003", str(ex))


@router.post("/api/agent/skill/list")
async def public_agent_skill_list(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:view", "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.skills(principal.tenant_id, payload.get("agentId")))
    except ValueError as ex:
        return api_fail("AI_AGENT_SKILL_001", str(ex))


@router.post("/api/agent/skill/save")
async def public_agent_skill_save(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.save_skill(principal.tenant_id, payload))
    except ValueError as ex:
        return api_fail("AI_AGENT_SKILL_002", str(ex))


@router.post("/api/agent/skill/delete")
async def public_agent_skill_delete(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.delete_skill(principal.tenant_id, payload.get("id")))
    except ValueError as ex:
        return api_fail("AI_AGENT_SKILL_003", str(ex))


@router.post("/api/agent/token/today")
async def public_agent_token_today(http_request: Request):
    principal = require_current_principal(http_request)
    try:
        return api_ok(await agent_management_repository.token_today(principal.tenant_id, principal.user_id))
    except ValueError as ex:
        return api_fail("AI_AGENT_TOKEN_001", str(ex))


@router.post("/api/agent/token/quota/overview")
async def public_agent_token_quota_overview(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:manage")
    try:
        return api_ok(await agent_management_repository.token_quota_overview(principal.tenant_id))
    except ValueError as ex:
        return api_fail("AI_AGENT_TOKEN_002", str(ex))


@router.post("/api/agent/token/quota/assign")
async def public_agent_token_quota_assign(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.assign_token_quota(principal.tenant_id, payload))
    except ValueError as ex:
        return api_fail("AI_AGENT_TOKEN_003", str(ex))


@router.post("/api/agent/token/quota/clear")
async def public_agent_token_quota_clear(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await agent_management_repository.clear_token_quota(principal.tenant_id, payload.get("userId")))
    except ValueError as ex:
        return api_fail("AI_AGENT_TOKEN_004", str(ex))


@router.post("/api/agent-assistant/agents")
async def public_assistant_agents(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:assistant:use", "crm:agent:manage")
    return api_ok(await assistant_repository.agents(principal.tenant_id, principal.user_id))


@router.post("/api/agent-assistant/conversations")
async def public_assistant_conversations(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:assistant:use", "crm:agent:manage")
    payload = await json_body(http_request)
    return api_ok(await assistant_repository.conversations(
        principal.tenant_id,
        principal.user_id,
        payload.get("agentId"),
    ))


@router.post("/api/agent-assistant/messages")
async def public_assistant_messages(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:assistant:use", "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        return api_ok(await assistant_repository.messages(
            principal.tenant_id,
            principal.user_id,
            payload.get("conversationId"),
        ))
    except ValueError as ex:
        return api_fail("AI_ASSISTANT_001", str(ex))


@router.post("/api/agent-assistant/conversation/delete")
async def public_assistant_delete_conversation(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:assistant:use", "crm:agent:manage")
    payload = await json_body(http_request)
    try:
        await assistant_repository.delete_conversation(
            principal.tenant_id,
            principal.user_id,
            payload.get("conversationId"),
        )
        return api_ok(None)
    except ValueError as ex:
        return api_fail("AI_ASSISTANT_002", str(ex))


@router.post("/api/agent-assistant/run/stream")
async def public_assistant_run_stream(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:assistant:use", "crm:agent:manage")
    payload = with_principal(await json_body(http_request), principal)
    request = AssistantRunRequest.model_validate(payload)
    return StreamingResponse(
        assistant_stream_generator(
            request,
            http_request.headers.get("Authorization"),
            http_request.headers.get("X-Trace-Id"),
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/api/agent-assistant/run/stop")
async def public_assistant_stop_run(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:assistant:use", "crm:agent:manage")
    payload = with_principal(await json_body(http_request), principal)
    request = AssistantStopRequest.model_validate(payload)
    key = active_key(request.tenant_id, request.user_id, request.request_id)
    task = active_tasks.get(key)
    if task is None:
        return api_ok(False)
    task.cancel()
    return api_ok(True)


@router.post("/api/assistant/langgraph/lead/analyze")
@router.post("/api/assistant/lead/analyze")
async def public_lead_analyze(http_request: Request):
    principal = require_current_principal(http_request)
    require_any_authority(principal, "crm:assistant:use", "crm:agent:manage")
    require_any_authority(principal, "crm:lead:view", "crm:lead:manage")
    payload = await json_body(http_request)
    lead_id = payload.get("leadId")
    if not lead_id:
        return api_fail("AI_LEAD_001", "线索编号不能为空")
    try:
        lead = await business_repository.lead_detail(
            principal.tenant_id,
            principal.user_id,
            principal.data_scope,
            str(lead_id),
        )
        runtime_request = RuntimeRunRequest(
            tenantId=principal.tenant_id,
            userId=principal.user_id,
            sceneCode="LEAD_ANALYZE",
            businessType="LEAD",
            businessId=str(lead_id),
            sessionId="lead-analysis-" + str(lead_id),
            injectedPrompt=payload.get("instruction"),
            message=build_lead_analyze_message(lead, payload.get("instruction")),
            context={
                "businessType": "LEAD",
                "leadId": str(lead_id),
                "lead": lead,
                "dataScope": principal.data_scope,
                "permissions": principal.permissions,
                "conversationTitle": "线索分析：" + str(lead.get("companyName") or lead.get("name") or lead_id),
            },
            authorization=http_request.headers.get("Authorization"),
        )
        response = await execute_runtime(runtime_request)
        result = business_repository.parse_result(response.output)
        if not result:
            return api_ok({
                "leadId": lead.get("id"),
                "leadName": lead.get("name"),
                "available": False,
                "success": False,
                "message": "线索智能体未生成结构化结论",
                "lead": lead,
                "runtimeEvents": [item.model_dump(by_alias=True, mode="json") for item in response.events],
                "runId": response.run_id,
                "conversationId": response.conversation_id,
            })
        draft = result.get("convertDraft") if isinstance(result.get("convertDraft"), dict) else {}
        saved_lead = await business_repository.save_lead_ai_analysis(
            principal.tenant_id,
            str(lead_id),
            business_repository.lead_ai_response(
                lead,
                result,
                response.run_id,
                response.conversation_id,
                [],
            ).get("summary"),
            draft.get("customerName") or lead.get("companyName") or lead.get("name"),
            draft.get("contactName") or lead.get("name"),
            result.get("confidence"),
        )
        return api_ok(business_repository.lead_ai_response(
            lead,
            result,
            response.run_id,
            response.conversation_id,
            [item.model_dump(by_alias=True, mode="json") for item in response.events],
            saved_lead,
        ))
    except HTTPException as ex:
        if ex.status_code == 403:
            return api_fail("DATA_001", str(ex.detail))
        if ex.status_code == 404:
            return api_fail("LEAD_404", str(ex.detail))
        raise
    except ValueError as ex:
        return api_fail("AI_LEAD_002", str(ex))
    except RuntimeError as ex:
        return api_fail("AI_LEAD_003", str(ex))
    except Exception as ex:
        logger.exception("线索AI分析执行失败，线索编号：%s", lead_id)
        message = str(ex).strip() or type(ex).__name__
        return api_fail("AI_LEAD_003", "线索AI分析失败：" + message)


@router.post("/internal/ai/runtime/run", response_model=RuntimeRunResponse)
async def run_runtime(
    runtime_request: RuntimeRunRequest,
    http_request: Request,
    _: None = Depends(require_internal_token),
):
    enrich_trace_context(runtime_request, http_request)
    runtime_request.authorization = http_request.headers.get("Authorization")
    return await execute_runtime(runtime_request)


@router.post("/internal/ai/lead/analyze", response_model=RuntimeRunResponse)
async def analyze_lead(
    runtime_request: RuntimeRunRequest,
    http_request: Request,
    _: None = Depends(require_internal_token),
):
    enrich_trace_context(runtime_request, http_request)
    runtime_request.authorization = http_request.headers.get("Authorization")
    runtime_request.scene_code = "LEAD_ANALYZE"
    return await execute_runtime(runtime_request)


@router.post("/internal/ai/assistant/agents")
async def assistant_agents(
    request: AssistantAgentListRequest,
    _: None = Depends(require_internal_token),
):
    return await assistant_repository.agents(request.tenant_id, request.user_id)


@router.post("/internal/ai/assistant/conversations")
async def assistant_conversations(
    request: AssistantConversationListRequest,
    _: None = Depends(require_internal_token),
):
    return await assistant_repository.conversations(request.tenant_id, request.user_id, request.agent_id)


@router.post("/internal/ai/assistant/messages")
async def assistant_messages(
    request: AssistantMessagesRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await assistant_repository.messages(request.tenant_id, request.user_id, request.conversation_id)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/assistant/conversation/delete")
async def assistant_delete_conversation(
    request: AssistantConversationActionRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await assistant_repository.delete_conversation(request.tenant_id, request.user_id, request.conversation_id)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/assistant/run/stream")
async def assistant_run_stream(
    request: AssistantRunRequest,
    http_request: Request,
    _: None = Depends(require_internal_token),
):
    enrich_assistant_context(request, http_request)
    return StreamingResponse(
        assistant_stream_generator(
            request,
            http_request.headers.get("Authorization"),
            http_request.headers.get("X-Trace-Id"),
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/internal/ai/assistant/run/stop")
async def assistant_stop_run(
    request: AssistantStopRequest,
    _: None = Depends(require_internal_token),
):
    key = active_key(request.tenant_id, request.user_id, request.request_id)
    task = active_tasks.get(key)
    if task is None:
        return False
    task.cancel()
    return True


@router.post("/internal/ai/agent/page")
async def agent_page(
    request: AgentManageRequest,
    _: None = Depends(require_internal_token),
):
    return await agent_management_repository.page(request.tenant_id, request.page_no, request.page_size)


@router.post("/internal/ai/agent/detail")
async def agent_detail(
    request: AgentIdManageRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await agent_management_repository.detail(request.tenant_id, request.id)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/agent/save")
async def agent_save(
    request: AgentSaveManageRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await agent_management_repository.save_agent(
            request.tenant_id,
            request.model_dump(by_alias=True, mode="json"),
        )
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/agent/mcp/list")
async def agent_mcp_list(
    request: AgentIdManageRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await agent_management_repository.mcps(request.tenant_id, request.agent_id)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/agent/mcp/save")
async def agent_mcp_save(
    request: AgentMcpSaveManageRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await agent_management_repository.save_mcp(
            request.tenant_id,
            request.model_dump(by_alias=True, mode="json"),
        )
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/agent/mcp/delete")
async def agent_mcp_delete(
    request: AgentIdManageRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await agent_management_repository.delete_mcp(request.tenant_id, request.id)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/agent/skill/list")
async def agent_skill_list(
    request: AgentIdManageRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await agent_management_repository.skills(request.tenant_id, request.agent_id)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/agent/skill/save")
async def agent_skill_save(
    request: AgentSkillSaveManageRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await agent_management_repository.save_skill(
            request.tenant_id,
            request.model_dump(by_alias=True, mode="json"),
        )
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/agent/skill/delete")
async def agent_skill_delete(
    request: AgentIdManageRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await agent_management_repository.delete_skill(request.tenant_id, request.id)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/agent/token/today")
async def agent_token_today(
    request: AgentManageRequest,
    _: None = Depends(require_internal_token),
):
    return await agent_management_repository.token_today(request.tenant_id, request.user_id)


@router.post("/internal/ai/agent/token/quota/overview")
async def agent_token_quota_overview(
    request: AgentManageRequest,
    _: None = Depends(require_internal_token),
):
    return await agent_management_repository.token_quota_overview(request.tenant_id)


@router.post("/internal/ai/agent/token/quota/assign")
async def agent_token_quota_assign(
    request: AgentTokenQuotaAssignManageRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await agent_management_repository.assign_token_quota(
            request.tenant_id,
            request.model_dump(by_alias=True, mode="json"),
        )
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


@router.post("/internal/ai/agent/token/quota/clear")
async def agent_token_quota_clear(
    request: AgentTokenQuotaClearManageRequest,
    _: None = Depends(require_internal_token),
):
    try:
        return await agent_management_repository.clear_token_quota(
            request.tenant_id,
            request.target_user_id or request.clear_user_id,
        )
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex


async def execute_runtime(request: RuntimeRunRequest) -> RuntimeRunResponse:
    try:
        return await agent_runtime_executor.run(
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
    trace_id = http_request.headers.get("X-Trace-Id")
    if trace_id:
        runtime_request.context["traceId"] = trace_id


def enrich_assistant_context(request: AssistantRunRequest, http_request: Request) -> None:
    trace_id = http_request.headers.get("X-Trace-Id")
    if trace_id:
        request.model_extra["traceId"] = trace_id


async def assistant_stream_generator(
        request: AssistantRunRequest,
        authorization: str | None = None,
        trace_id: str | None = None):
    key = active_key(request.tenant_id, request.user_id, request.request_id)
    if key in active_tasks:
        yield sse("RUN_ERROR", {"type": "RUN_ERROR", "content": "本次回答已经在运行中"})
        return
    task = asyncio.current_task()
    if task is not None:
        active_tasks[key] = task
    run_request = None
    run_task = None
    streamed_answer = False
    try:
        yield sse("RUN_STATUS_CHANGED", {
            "type": "RUN_STATUS_CHANGED",
            "stage": "运行状态变更",
            "content": "智能体开始处理",
        })
        run_request = await build_assistant_runtime_request(request, authorization, trace_id)
        stream = RuntimeStream()

        async def run_with_stream():
            token = set_runtime_stream(stream)
            try:
                return await execute_runtime(run_request)
            finally:
                reset_runtime_stream(token)
                await stream.close()

        run_task = asyncio.create_task(run_with_stream())
        active_tasks[key] = run_task
        while True:
            stream_payload = await stream.queue.get()
            if stream_payload is STREAM_END:
                break
            payload = dict(stream_payload or {})
            payload["runId"] = payload.get("runId") or run_request.run_id
            payload["conversationId"] = payload.get("conversationId") or run_request.conversation_id
            event_type = payload.get("type") or "RUNTIME_EVENT"
            if event_type == "ANSWER_DELTA":
                streamed_answer = True
            yield sse(event_type, payload)

        response = await run_task
        if not streamed_answer:
            chunk_size = max(1, min(int(settings.assistant_stream_chunk_size or 8), 40))
            delay_seconds = max(0, min(int(settings.assistant_stream_delay_ms or 0), 200)) / 1000
            for chunk in split_text(response.output, chunk_size):
                yield sse("ANSWER_DELTA", {
                    "type": "ANSWER_DELTA",
                    "stage": "回答增量",
                    "content": chunk,
                    "metadata": {},
                    "runId": response.run_id,
                    "conversationId": response.conversation_id,
                })
                if delay_seconds > 0:
                    await asyncio.sleep(delay_seconds)
        yield sse("ANSWER_FINISHED", {
            "type": "ANSWER_FINISHED",
            "stage": "回答完成",
            "content": "",
            "metadata": {},
            "runId": response.run_id,
            "conversationId": response.conversation_id,
        })
        yield sse("RUN_FINISHED", {
            "type": "RUN_FINISHED",
            "stage": "运行完成",
            "content": "智能体回复完成",
            "metadata": {
                "response": {
                    "success": True,
                    "reply": response.output,
                    "runId": response.run_id,
                    "conversationId": response.conversation_id,
                    "message": "智能体回复完成",
                }
            },
            "runId": response.run_id,
            "conversationId": response.conversation_id,
        })
    except asyncio.CancelledError:
        yield sse("RUN_ERROR", {
            "type": "RUN_ERROR",
            "stage": "运行终止",
            "content": "本次回答已终止",
            "runId": run_request.run_id if run_request else None,
            "conversationId": run_request.conversation_id if run_request else request.conversation_id,
        })
    except Exception as ex:
        yield sse("RUN_ERROR", {
            "type": "RUN_ERROR",
            "stage": "运行异常",
            "content": str(ex),
            "runId": run_request.run_id if run_request else None,
            "conversationId": run_request.conversation_id if run_request else request.conversation_id,
        })
    finally:
        active_tasks.pop(key, None)


async def build_assistant_runtime_request(
        request: AssistantRunRequest,
        authorization: str | None = None,
        trace_id: str | None = None) -> RuntimeRunRequest:
    agent_payload = await assistant_repository.agent_runtime_payload(request.tenant_id, request.agent_id)
    agent = RuntimeAgent.model_validate(agent_payload)
    context = {
        "userMessage": request.message,
        "attachments": [item.model_dump(by_alias=True, mode="json") for item in request.attachments],
        "conversationTitle": title(request.message),
        "dataScope": request.data_scope or "SELF",
        "permissions": request.permissions,
    }
    if trace_id:
        context["traceId"] = trace_id
    return RuntimeRunRequest(
        tenantId=request.tenant_id,
        userId=request.user_id,
        conversationId=request.conversation_id,
        sceneCode=agent.scene_code or "GENERAL_ASSISTANT",
        message=request.message,
        sessionId="conversation-" + request.conversation_id if request.conversation_id else None,
        context=context,
        agent=agent,
        authorization=authorization,
    )


def active_key(tenant_id: str, user_id: str, request_id: str) -> str:
    return f"{tenant_id}:{user_id}:{request_id}"


def title(value: str) -> str:
    text = (value or "").strip().replace("\n", " ")
    if len(text) <= 24:
        return text or "新会话"
    return text[:24] + "…"


def split_text(value: str, size: int):
    text = value or ""
    if not text:
        return []
    return [text[index:index + size] for index in range(0, len(text), size)]


def build_lead_analyze_message(lead: dict, instruction: str | None) -> str:
    values = [
        "请分析以下真实线索数据，输出销售可直接使用的结构化结论。",
        "如果存在公司名称，可以检索公开客户信息；公开信息只用于补充公司规模、行业、来源链接。",
        "最终必须通过当前场景提供的结构化输出工具提交结果，不要用普通文本代替。",
    ]
    if instruction:
        values.append("补充要求：" + str(instruction).strip())
    return "\n".join(values)


def sse(event_name: str, payload: dict) -> str:
    return "event: %s\ndata: %s\n\n" % (
        event_name,
        json.dumps(payload, ensure_ascii=False, default=str),
    )
