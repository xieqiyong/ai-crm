import json
import re

from langchain.agents import create_agent
from langchain.agents.middleware import ModelCallLimitMiddleware
from langchain.agents.structured_output import ToolStrategy
from langchain_core.tools import BaseTool

from app.agents.config import agent_config, workflow_code
from app.agents.middleware import AgentObservabilityMiddleware
from app.mcp.registry import McpToolBundle, mcp_registry
from app.models.chat_model_factory import chat_model_factory
from app.runtime.bundle import RuntimeGraphBundle
from app.runtime.context import RuntimeContext
from app.runtime.execution_context import AgentExecutionContext
from app.schemas.lead_analysis import LeadAnalysisResult
from app.schemas.runtime import RuntimeRunRequest
from app.tools.builtin import load_skill


class ToolCallingAgentFactory:
    async def build(
            self,
            request: RuntimeRunRequest,
            runtime_context: RuntimeContext,
            checkpointer=None,
            excluded_tool_names: set[str] | None = None,
            workflow_prompt: str | None = None) -> RuntimeGraphBundle:
        if request.agent is None:
            raise RuntimeError("当前场景没有可用的智能体配置")
        response_format = self._response_format(request)
        model = chat_model_factory.build(
            request.agent,
            force_tool_choice=response_format is not None,
        )
        mcp_bundle = await mcp_registry.load_tools(runtime_context.mcps)
        tools = self._tools(runtime_context, mcp_bundle, excluded_tool_names or set())
        execution_context = AgentExecutionContext.from_request(request, runtime_context.skills)
        max_model_calls = self._max_model_calls(request)
        graph = create_agent(
            model=model,
            tools=tools,
            system_prompt=self._system_prompt(request, runtime_context, tools, workflow_prompt),
            middleware=[
                ModelCallLimitMiddleware(
                    run_limit=max_model_calls,
                    exit_behavior="error",
                ),
                AgentObservabilityMiddleware(),
            ],
            response_format=response_format,
            context_schema=AgentExecutionContext,
            checkpointer=checkpointer,
            name=self._agent_name(request),
        )
        return RuntimeGraphBundle(
            graph=graph,
            execution_context=execution_context,
            mcp_bundle=mcp_bundle,
            input_state={
                "messages": [{"role": "user", "content": self.user_message(request)}],
            },
        )

    def _max_model_calls(self, request: RuntimeRunRequest) -> int:
        value = request.agent.max_iters if request.agent and request.agent.max_iters else 8
        return min(max(int(value), 1), 50)

    def _tools(
            self,
            runtime_context: RuntimeContext,
            mcp_bundle: McpToolBundle,
            excluded_tool_names: set[str]) -> list[BaseTool]:
        values = [item.tool for item in runtime_context.tools if item.tool.name not in excluded_tool_names]
        if runtime_context.skills:
            values.append(load_skill)
        values.extend(mcp_bundle.tools)
        result: list[BaseTool] = []
        names: set[str] = set()
        for value in values:
            if value.name in names:
                raise RuntimeError("智能体工具名称重复：%s" % value.name)
            names.add(value.name)
            result.append(value)
        return result

    def _system_prompt(
            self,
            request: RuntimeRunRequest,
            runtime_context: RuntimeContext,
            tools: list[BaseTool],
            workflow_prompt: str | None = None) -> str:
        values = []
        if self._text(request.rendered_system_prompt):
            values.append(self._text(request.rendered_system_prompt))
        elif request.agent and self._text(request.agent.system_prompt):
            values.append(self._text(request.agent.system_prompt))
        if self._text(request.injected_prompt):
            values.append("本次运行附加要求：\n" + self._text(request.injected_prompt))
        values.append(
            "你是智能营销管理系统中的企业级AI智能体。只能依据用户输入、当前会话、"
            "经过权限过滤的CRM数据、知识库和工具结果回答，禁止编造业务数据。"
        )
        values.append(
            "需要外部信息时必须调用相应工具；不要向用户描述工具调用步骤，不要直接倾倒原始检索结果，"
            "应当基于证据形成清晰、可执行的中文结论。"
        )
        skill_catalog = self._skill_catalog(runtime_context)
        if skill_catalog:
            values.append(skill_catalog)
        if self._text(workflow_prompt):
            values.append(self._text(workflow_prompt))
        if workflow_code(request.agent, request.scene_code) == "LEAD_ANALYSIS":
            if not workflow_prompt:
                values.append("存在公司名称时必须先调用customer_web_search。")
            values.append(
                "当前任务是线索分析。需要产品、方案、案例或话术时调用knowledge_hybrid_search。"
                "最终必须通过结构化输出工具提交LeadAnalysisResult，不得以普通文本代替结构化结果。"
                "客户公开档案只保留公司规模、行业和最多三个来源链接。"
            )
        if not tools:
            values.append("当前智能体没有挂载任何工具，只能依据已提供的真实上下文回答。")
        return "\n\n".join(values)

    def _skill_catalog(self, runtime_context: RuntimeContext) -> str:
        if not runtime_context.skills:
            return ""
        lines = [
            "当前挂载的Skill如下。这里只提供技能目录；确定需要使用某项技能后，先调用load_skill读取完整规范："
        ]
        for skill in runtime_context.skills:
            code = skill.code or skill.id or skill.name or "unknown"
            description = skill.description or "按该技能的完整内容执行"
            lines.append("- %s｜%s｜%s" % (code, skill.name or code, description))
        return "\n".join(lines)

    def _response_format(self, request: RuntimeRunRequest):
        config = agent_config(request.agent)
        configured = config.get("responseFormat") or config.get("response_format")
        if isinstance(configured, dict):
            schema = configured.get("schema") if isinstance(configured.get("schema"), dict) else configured
            if str(schema.get("type") or "").strip().lower() != "object":
                raise RuntimeError("智能体结构化输出配置必须是JSON对象Schema")
            if not str(schema.get("title") or "").strip():
                schema = dict(schema)
                schema["title"] = "agent_structured_result"
            message = configured.get("toolMessageContent") or configured.get("tool_message_content")
            return ToolStrategy(
                schema,
                tool_message_content=str(message or "结构化结果已生成。"),
                handle_errors=True,
            )
        if workflow_code(request.agent, request.scene_code) == "LEAD_ANALYSIS":
            return ToolStrategy(
                LeadAnalysisResult,
                tool_message_content="线索结构化分析结果已生成。",
                handle_errors=True,
            )
        return None

    def _agent_name(self, request: RuntimeRunRequest) -> str:
        source = "crm_" + ((request.scene_code or "general_assistant").strip().lower())
        value = re.sub(r"[^a-z0-9_-]+", "_", source).strip("_")
        return value or "crm_general_assistant"

    def user_message(self, request: RuntimeRunRequest) -> str:
        values = [self._text(request.message)]
        business_context = request.context.get("businessContext")
        if business_context:
            values.append("当前业务上下文：\n" + self._serialize(business_context))
        attachments = request.context.get("attachments")
        if attachments:
            values.append("本次上传附件：\n" + self._serialize(attachments))
        return "\n\n".join(value for value in values if value)

    def _serialize(self, value) -> str:
        if isinstance(value, str):
            return value
        return json.dumps(value, ensure_ascii=False, default=str)

    def _text(self, value) -> str:
        if value is None:
            return ""
        return str(value).strip()


tool_calling_agent_factory = ToolCallingAgentFactory()
