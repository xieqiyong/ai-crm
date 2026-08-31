import json
from typing import Any

from langchain.tools import ToolRuntime, tool

from app.core.credentials import runtime_credential_store
from app.runtime.execution_context import AgentExecutionContext
from app.reports.service import report_service
from app.services.crm_api import crm_api_client
from app.tools.customer_web_search import customer_web_search


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, default=str)


def _page_payload(page_no: int, page_size: int, **values: Any) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "pageNo": max(int(page_no or 1), 1),
        "pageSize": min(max(int(page_size or 10), 1), 20),
    }
    for key, value in values.items():
        if value is not None and str(value).strip():
            payload[key] = value
    return payload


def _emit(runtime: ToolRuntime[AgentExecutionContext], content: str, tool_name: str) -> None:
    runtime.stream_writer({
        "type": "TOOL_STATUS",
        "content": content,
        "toolName": tool_name,
    })


def _authorization(runtime: ToolRuntime[AgentExecutionContext]) -> str | None:
    return runtime_credential_store.authorization(runtime.context.credential_key)


@tool("customer_web_search")
async def customer_web_search_tool(
        runtime: ToolRuntime[AgentExecutionContext],
        company_name: str) -> str:
    """检索指定公司的公开资料，只返回能够从公开来源确认的信息和来源链接。"""
    _emit(runtime, "正在检索客户公开资料", "customer_web_search")
    result = await customer_web_search.search(company_name)
    return _json(result)


@tool
async def knowledge_hybrid_search(
        runtime: ToolRuntime[AgentExecutionContext],
        query: str,
        top_k: int = 5,
        category: str = "",
        source_type: str = "") -> str:
    """使用向量检索、关键词检索和重排查询公司知识库，适合查产品、方案、案例、话术和内部资料。"""
    _emit(runtime, "正在检索公司知识库", "knowledge_hybrid_search")
    result = await crm_api_client.post(
        "/api/knowledge/document/search",
        {
            "query": query,
            "topK": min(max(int(top_k or 5), 1), 10),
            "category": category or None,
            "sourceType": source_type or None,
        },
        _authorization(runtime),
        runtime.context.trace_id,
    )
    return _json(result)


@tool
async def load_skill(
        runtime: ToolRuntime[AgentExecutionContext],
        skill_code: str) -> str:
    """按技能编码加载已挂载Skill的完整操作规范；决定使用某项技能后必须先调用本工具。"""
    skill = runtime.context.find_skill(skill_code)
    if skill is None:
        available = [item.code or item.name for item in runtime.context.skills]
        raise ValueError("未挂载该Skill，可用Skill：%s" % "、".join(item for item in available if item))
    _emit(runtime, "正在加载技能：" + str(skill.name or skill.code), "load_skill")
    return _json({
        "code": skill.code,
        "name": skill.name,
        "description": skill.description,
        "content": skill.content,
        "config": skill.config,
    })


@tool
async def generate_report(
        runtime: ToolRuntime[AgentExecutionContext],
        title: str,
        content: str,
        formats: list[str] | None = None) -> str:
    """将完整的Markdown报告生成Word、PDF或HTML文件；仅在用户明确要求生成或下载报告时调用。"""
    _emit(runtime, "正在生成报告文件", "generate_report")
    reports = await report_service.generate(
        tenant_id=runtime.context.tenant_id,
        user_id=runtime.context.user_id,
        run_id=runtime.context.run_id,
        conversation_id=runtime.context.conversation_id,
        title=title,
        content=content,
        formats=formats,
    )
    runtime.stream_writer({
        "type": "REPORT_READY",
        "content": "报告文件已生成",
        "toolName": "generate_report",
        "metadata": {"reports": reports},
    })
    return _json({
        "success": True,
        "message": "报告文件已生成，请提示用户从文件卡片下载。",
        "reports": [
            {
                "artifactId": item.get("artifactId"),
                "fileName": item.get("fileName"),
                "format": item.get("format"),
                "size": item.get("size"),
            }
            for item in reports
        ],
    })


@tool
async def crm_lead_page(
        runtime: ToolRuntime[AgentExecutionContext],
        keyword: str = "",
        status: str = "",
        page_no: int = 1,
        page_size: int = 10) -> str:
    """分页查询当前用户有权限查看的真实线索，支持名称、公司、电话等关键词和线索状态。"""
    _emit(runtime, "正在查询线索", "crm_lead_page")
    result = await crm_api_client.post(
        "/api/lead/page",
        _page_payload(page_no, page_size, keyword=keyword, status=status),
        _authorization(runtime),
        runtime.context.trace_id,
    )
    return _json(result)


@tool
async def crm_lead_detail(
        runtime: ToolRuntime[AgentExecutionContext],
        lead_id: str) -> str:
    """按线索编号查询当前用户有权限查看的真实线索详情。"""
    _emit(runtime, "正在读取线索详情", "crm_lead_detail")
    result = await crm_api_client.post(
        "/api/lead/detail",
        {"id": lead_id},
        _authorization(runtime),
        runtime.context.trace_id,
    )
    return _json(result)


@tool
async def crm_customer_page(
        runtime: ToolRuntime[AgentExecutionContext],
        keyword: str = "",
        status: str = "",
        page_no: int = 1,
        page_size: int = 10) -> str:
    """分页查询当前用户有权限查看的真实客户，支持客户名称、联系人等关键词和客户状态。"""
    _emit(runtime, "正在查询客户", "crm_customer_page")
    result = await crm_api_client.post(
        "/api/customer/page",
        _page_payload(page_no, page_size, keyword=keyword, status=status),
        _authorization(runtime),
        runtime.context.trace_id,
    )
    return _json(result)


@tool
async def crm_customer_detail(
        runtime: ToolRuntime[AgentExecutionContext],
        customer_id: str) -> str:
    """按客户编号查询当前用户有权限查看的真实客户详情。"""
    _emit(runtime, "正在读取客户详情", "crm_customer_detail")
    result = await crm_api_client.post(
        "/api/customer/detail",
        {"id": customer_id},
        _authorization(runtime),
        runtime.context.trace_id,
    )
    return _json(result)


@tool
async def crm_followup_page(
        runtime: ToolRuntime[AgentExecutionContext],
        keyword: str = "",
        target_type: str = "",
        target_id: str = "",
        followup_type: str = "",
        page_no: int = 1,
        page_size: int = 10) -> str:
    """分页查询当前用户有权限查看的真实跟进记录，可按线索或客户及跟进方式筛选。"""
    _emit(runtime, "正在查询跟进记录", "crm_followup_page")
    result = await crm_api_client.post(
        "/api/followup/page",
        _page_payload(
            page_no,
            page_size,
            keyword=keyword,
            targetType=target_type,
            targetId=target_id,
            followupType=followup_type,
        ),
        _authorization(runtime),
        runtime.context.trace_id,
    )
    return _json(result)


@tool
async def crm_followup_detail(
        runtime: ToolRuntime[AgentExecutionContext],
        followup_id: str) -> str:
    """按跟进记录编号查询当前用户有权限查看的真实跟进详情。"""
    _emit(runtime, "正在读取跟进详情", "crm_followup_detail")
    result = await crm_api_client.post(
        "/api/followup/detail",
        {"id": followup_id},
        _authorization(runtime),
        runtime.context.trace_id,
    )
    return _json(result)


@tool
async def crm_opportunity_page(
        runtime: ToolRuntime[AgentExecutionContext],
        keyword: str = "",
        stage: str = "",
        customer_id: str = "",
        page_no: int = 1,
        page_size: int = 10) -> str:
    """分页查询当前用户有权限查看的真实商机，支持关键词、商机阶段和客户筛选。"""
    _emit(runtime, "正在查询商机", "crm_opportunity_page")
    result = await crm_api_client.post(
        "/api/opportunity/page",
        _page_payload(page_no, page_size, keyword=keyword, stage=stage, customerId=customer_id),
        _authorization(runtime),
        runtime.context.trace_id,
    )
    return _json(result)


@tool
async def crm_opportunity_detail(
        runtime: ToolRuntime[AgentExecutionContext],
        opportunity_id: str) -> str:
    """按商机编号查询当前用户有权限查看的真实商机详情和产品报价。"""
    _emit(runtime, "正在读取商机详情", "crm_opportunity_detail")
    result = await crm_api_client.post(
        "/api/opportunity/detail",
        {"id": opportunity_id},
        _authorization(runtime),
        runtime.context.trace_id,
    )
    return _json(result)
