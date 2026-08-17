import operator
from typing import Annotated, Any, TypedDict

from langgraph.graph import END, START, StateGraph

from app.graphs.observability import observed_node
from app.runtime.streaming import runtime_streaming_enabled
from app.schemas.runtime import RuntimeRunRequest
from app.services.event_bus import runtime_event
from app.services.llm import OpenAICompatibleClient, LlmResult, text


class GenericAssistantState(TypedDict, total=False):
    request: dict[str, Any]
    runtime: dict[str, Any]
    output: str
    usage: dict[str, int]
    events: Annotated[list[dict[str, Any]], operator.add]


llm_client = OpenAICompatibleClient()


def build_graph(checkpointer=None):
    builder = StateGraph(GenericAssistantState)
    builder.add_node("prepare_context", observed_node("prepare_context", "读取会话上下文", prepare_context))
    builder.add_node("assistant_reply", observed_node("assistant_reply", "生成智能体回复", assistant_reply))
    builder.add_node("finalize", observed_node("finalize", "整理回复结果", finalize))
    builder.add_edge(START, "prepare_context")
    builder.add_edge("prepare_context", "assistant_reply")
    builder.add_edge("assistant_reply", "finalize")
    builder.add_edge("finalize", END)
    return builder.compile(checkpointer=checkpointer)


async def prepare_context(state: GenericAssistantState) -> dict[str, Any]:
    return {
        "events": [
            runtime_event("STEP", content="读取当前会话上下文", metadata={"node": "prepare_context", "status": "SUCCESS"}),
        ],
    }


async def assistant_reply(state: GenericAssistantState) -> dict[str, Any]:
    request = runtime_request(state)
    system_prompt = build_system_prompt(request)
    user_prompt = build_user_prompt(request)
    if runtime_streaming_enabled():
        llm_result = await llm_client.stream_chat(request.agent, system_prompt, user_prompt)
    else:
        llm_result = await llm_client.chat(request.agent, system_prompt, user_prompt)
    return {
        "output": llm_result.content,
        "usage": usage_dict(llm_result),
        "events": [
            runtime_event("STEP", content="整理智能体回复", metadata={"node": "assistant_reply", "status": "SUCCESS"}),
        ],
    }


async def finalize(state: GenericAssistantState) -> dict[str, Any]:
    metadata = {"node": "finalize", "status": "SUCCESS"}
    metadata.update(state.get("usage") or {})
    return {
        "events": [
            runtime_event("FINAL_RESULT", content=state.get("output") or "", metadata=metadata),
        ],
    }


def runtime_request(state: GenericAssistantState) -> RuntimeRunRequest:
    return RuntimeRunRequest.model_validate(state.get("request") or {})


def build_system_prompt(request: RuntimeRunRequest) -> str:
    prompts = []
    if text(request.rendered_system_prompt):
        prompts.append(text(request.rendered_system_prompt))
    elif request.agent and text(request.agent.system_prompt):
        prompts.append(text(request.agent.system_prompt))
    skill_prompt = build_skill_prompt(request)
    if skill_prompt:
        prompts.append(skill_prompt)
    prompts.append("你是智能营销管理系统的智能体助手。只能基于用户问题、会话上下文、系统业务数据和已挂载技能回答。")
    prompts.append("涉及产品、客户、商机、线索、渠道、跟进记录时，不能编造系统中不存在的数据。")
    prompts.append("回答使用清晰中文，优先给销售可直接执行的结论、依据和下一步动作。")
    return "\n\n".join(prompts)


def build_skill_prompt(request: RuntimeRunRequest) -> str:
    values = []
    for item in request.skills:
        content = text(item.content)
        if content:
            values.append(content)
    return "\n\n".join(values)


def build_user_prompt(request: RuntimeRunRequest) -> str:
    values = []
    history = request.context.get("history")
    if history:
        values.append("最近会话：\n" + text(history))
    business_context = request.context.get("businessContext")
    if business_context:
        values.append("当前业务上下文：\n" + text(business_context))
    attachments = request.context.get("attachments")
    if attachments:
        values.append("用户上传附件元信息：\n" + text(attachments))
    values.append("用户问题：\n" + text(request.message))
    return "\n\n".join(values)


def usage_dict(value: LlmResult) -> dict[str, int]:
    return {
        "inputTokens": value.input_tokens,
        "outputTokens": value.output_tokens,
        "totalTokens": value.total_tokens,
    }
