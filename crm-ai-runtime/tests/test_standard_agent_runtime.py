import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from langchain.agents import create_agent
from langchain.agents.middleware import ModelCallLimitMiddleware
from langchain.agents.middleware.model_call_limit import ModelCallLimitExceededError
from langchain.agents.structured_output import ToolStrategy
from langchain.messages import AIMessage, AIMessageChunk, ToolMessage
from langchain.tools import tool
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.outputs import ChatGeneration, ChatResult
from langgraph.checkpoint.memory import InMemorySaver

from app.agents.config import workflow_code
from app.agents.factory import tool_calling_agent_factory
from app.agents.models import AgentDefinition
from app.agents.repository import AgentRepository
from app.runtime.stream_adapter import AgentStreamAccumulator
from app.models.chat_model_factory import chat_model_factory
from app.models.openai_compatible import OpenAICompatibleChatModel
from app.runtime.execution_context import AgentExecutionContext
from app.runtime.agent_management_repository import AgentManagementRepository
from app.schemas.lead_analysis import LeadAnalysisResult
from app.schemas.runtime import RuntimeAgent, RuntimeRunRequest
from app.tools.builtin import crm_lead_page, load_skill
from app.tools.registry import ToolRegistry
from app.workflows.lead_analysis.nodes import lead_analysis_nodes
from app.workflows.lead_analysis.workflow import lead_analysis_workflow_factory


@tool
def echo_value(value: str) -> str:
    """返回输入值。"""
    return value


class ToolCallingFakeModel(BaseChatModel):
    @property
    def _llm_type(self) -> str:
        return "tool-calling-fake-model"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        tool_messages = [item for item in messages if isinstance(item, ToolMessage)]
        if tool_messages:
            message = AIMessage(
                content="工具结果：" + str(tool_messages[-1].content),
                usage_metadata={"input_tokens": 5, "output_tokens": 2, "total_tokens": 7},
            )
        else:
            message = AIMessage(
                content="",
                tool_calls=[{
                    "name": "echo_value",
                    "args": {"value": "真实结果"},
                    "id": "call-1",
                    "type": "tool_call",
                }],
                usage_metadata={"input_tokens": 3, "output_tokens": 1, "total_tokens": 4},
            )
        return ChatResult(generations=[ChatGeneration(message=message)])


class MessageCountFakeModel(BaseChatModel):
    @property
    def _llm_type(self) -> str:
        return "message-count-fake-model"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        return ChatResult(generations=[ChatGeneration(
            message=AIMessage(content="消息数量：%s" % len(messages)),
        )])


class StructuredOutputFakeModel(BaseChatModel):
    @property
    def _llm_type(self) -> str:
        return "structured-output-fake-model"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        return ChatResult(generations=[ChatGeneration(message=AIMessage(
            content="",
            tool_calls=[{
                "name": "LeadAnalysisResult",
                "args": {
                    "conclusionTitle": "建议继续跟进",
                    "salesConclusion": "已获得真实需求，暂未确认预算。",
                    "stage": "FOLLOWING",
                    "priority": "MEDIUM",
                    "recommendConvert": False,
                    "score": 65,
                    "confidence": 0.8,
                    "keyFindings": ["客户已表达需求"],
                    "riskWarnings": ["预算尚未确认"],
                    "nextActions": ["确认预算和决策人"],
                    "reason": "存在需求但关键信息不足",
                    "nextAction": "确认预算和决策人",
                    "convertDraft": {
                        "customerName": "测试企业",
                        "level": "NORMAL",
                        "status": "POTENTIAL",
                    },
                    "customerProfile": {
                        "companyScale": "",
                        "industry": "",
                        "sourceUrls": [],
                    },
                },
                "id": "structured-call-1",
                "type": "tool_call",
            }],
        ))])


class EndlessToolCallingFakeModel(BaseChatModel):
    @property
    def _llm_type(self) -> str:
        return "endless-tool-calling-fake-model"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        call_id = "call-%s" % len(messages)
        return ChatResult(generations=[ChatGeneration(message=AIMessage(
            content="",
            tool_calls=[{
                "name": "echo_value",
                "args": {"value": "继续调用"},
                "id": call_id,
                "type": "tool_call",
            }],
        ))])


class StandardAgentRuntimeTest(unittest.IsolatedAsyncioTestCase):
    async def test_scene_catalog_merges_custom_and_existing_scenes(self):
        managed = [{
            "id": 1,
            "tenant_id": 100,
            "code": "SCENE_1",
            "name": "自定义场景",
            "description": "测试场景",
            "sort_no": 1,
            "enabled": True,
            "agent_count": 0,
        }]
        inferred = [{
            "id": None,
            "tenant_id": 100,
            "code": "LEAD_ANALYZE",
            "name": "线索分析",
            "description": None,
            "sort_no": 0,
            "enabled": True,
            "agent_count": 2,
        }]
        with patch(
                "app.runtime.agent_management_repository.database_client.enabled",
                return_value=True), patch(
                "app.runtime.agent_management_repository.database_client.fetch_all",
                new_callable=AsyncMock,
                side_effect=[managed, inferred]):
            values = await AgentManagementRepository().scenes("100")
        self.assertEqual(2, len(values))
        self.assertTrue(values[0]["managed"])
        self.assertFalse(values[1]["managed"])
        self.assertEqual(2, values[1]["agentCount"])

    def test_custom_scene_can_mount_builtin_tools_independently(self):
        request = RuntimeRunRequest(
            tenantId="100",
            userId="200",
            sceneCode="LEAD_PROFILE_CUSTOM",
            context={"permissions": ["*"]},
        )
        agent = AgentDefinition(
            id="300",
            scene_code="LEAD_PROFILE_CUSTOM",
            extra_config_json='{"builtinTools":["crm_lead_detail","knowledge_hybrid_search"]}',
        )
        names = [item.name for item in ToolRegistry().resolve(request, agent)]
        self.assertEqual(["knowledge_hybrid_search", "crm_lead_detail"], names)

    def test_custom_scene_can_define_json_schema_output(self):
        request = RuntimeRunRequest(
            tenantId="100",
            userId="200",
            sceneCode="LEAD_PROFILE_CUSTOM",
            agent=RuntimeAgent(
                id="300",
                sceneCode="LEAD_PROFILE_CUSTOM",
                extraConfigJson=(
                    '{"responseFormat":{"title":"lead_profile_result","type":"object",'
                    '"properties":{"summary":{"type":"string"}},"required":["summary"]}}'
                ),
            ),
        )
        strategy = tool_calling_agent_factory._response_format(request)
        self.assertIsInstance(strategy, ToolStrategy)
        self.assertEqual("lead_profile_result", strategy.schema.get("title"))
        self.assertIn("summary", strategy.schema.get("properties"))

    def test_scene_agent_selection_uses_default_and_priority(self):
        rows = [
            {"id": 1, "extra_config_json": '{"defaultForScene":false,"scenePriority":99}'},
            {"id": 2, "extra_config_json": '{"defaultForScene":true,"scenePriority":10}'},
            {"id": 3, "extra_config_json": '{"defaultForScene":true,"scenePriority":20}'},
        ]
        selected = AgentRepository()._select_scene_agent(rows, "CUSTOM_SCENE")
        self.assertEqual(3, selected.get("id"))

    def test_custom_scene_defaults_to_standard_agent(self):
        agent = AgentDefinition(scene_code="CUSTOM_SCENE")
        self.assertEqual("STANDARD_AGENT", workflow_code(agent, agent.scene_code))
        configured = AgentDefinition(
            scene_code="CUSTOM_SCENE",
            extra_config_json='{"workflowCode":"LEAD_ANALYSIS"}',
        )
        self.assertEqual("LEAD_ANALYSIS", workflow_code(configured, configured.scene_code))

    async def test_agent_executes_standard_tool_loop(self):
        graph = create_agent(ToolCallingFakeModel(), [echo_value], name="test_tool_agent")
        self.assertEqual(
            {"__start__", "model", "tools", "__end__"},
            set(graph.get_graph().nodes.keys()),
        )
        accumulator = AgentStreamAccumulator()
        async for part in graph.astream(
                {"messages": [{"role": "user", "content": "调用工具"}]},
                stream_mode=["messages", "updates", "values", "custom"],
                version="v2"):
            await accumulator.consume(part)
        self.assertEqual("工具结果：真实结果", accumulator.output())
        self.assertEqual(11, accumulator.usage()["totalTokens"])
        self.assertEqual("echo_value", accumulator.events[0]["toolName"])

    async def test_checkpoint_retains_messages_between_runs(self):
        checkpointer = InMemorySaver()
        config = {"configurable": {"thread_id": "tenant:user:conversation"}}
        first_graph = create_agent(
            MessageCountFakeModel(),
            [],
            checkpointer=checkpointer,
            name="test_memory_agent",
        )
        await first_graph.ainvoke(
            {"messages": [{"role": "user", "content": "第一轮"}]},
            config=config,
        )
        second_graph = create_agent(
            MessageCountFakeModel(),
            [],
            checkpointer=checkpointer,
            name="test_memory_agent",
        )
        result = await second_graph.ainvoke(
            {"messages": [{"role": "user", "content": "第二轮"}]},
            config=config,
        )
        self.assertEqual(4, len(result["messages"]))
        self.assertEqual("消息数量：3", str(result["messages"][-1].content))

    async def test_tool_strategy_returns_validated_structured_response(self):
        graph = create_agent(
            StructuredOutputFakeModel(),
            [],
            response_format=ToolStrategy(LeadAnalysisResult),
            name="test_structured_agent",
        )
        result = await graph.ainvoke({"messages": [{"role": "user", "content": "分析线索"}]})
        structured = result.get("structured_response")
        self.assertIsInstance(structured, LeadAnalysisResult)
        self.assertEqual("建议继续跟进", structured.conclusion_title)
        self.assertEqual("POTENTIAL", structured.convert_draft.status)

    async def test_model_call_limit_stops_endless_tool_loop(self):
        graph = create_agent(
            EndlessToolCallingFakeModel(),
            [echo_value],
            middleware=[ModelCallLimitMiddleware(run_limit=2, exit_behavior="error")],
            name="test_call_limit_agent",
        )
        with self.assertRaises(ModelCallLimitExceededError):
            await graph.ainvoke({"messages": [{"role": "user", "content": "持续调用工具"}]})

    async def test_lead_analysis_uses_explicit_state_graph(self):
        analysis_agent = create_agent(
            StructuredOutputFakeModel(),
            [],
            response_format=ToolStrategy(LeadAnalysisResult),
            name="test_lead_analysis_inner_agent",
        )
        graph = lead_analysis_workflow_factory.compile(analysis_agent)
        self.assertEqual(
            {
                "__start__", "prepare_context", "company_web_search",
                "prepare_analysis", "analysis_agent", "record_analysis",
                "validate_output", "finalize_result", "__end__",
            },
            set(graph.get_graph().nodes.keys()),
        )
        result = await graph.ainvoke({
            "messages": [{"role": "user", "content": "分析线索"}],
            "lead": {"id": "1", "companyName": ""},
            "customer_profile": {},
        })
        self.assertIsInstance(result.get("structured_response"), LeadAnalysisResult)
        self.assertEqual("建议继续跟进", result["structured_response"].conclusion_title)

    async def test_lead_analysis_records_agent_node_elapsed_time(self):
        execution_context = AgentExecutionContext(
            tenant_id="100",
            user_id="200",
            scene_code="LEAD_ANALYZE",
            data_scope="SELF",
            permissions=("crm:lead:view",),
            business_type="LEAD",
            business_id="300",
            credential_key=None,
            trace_id=None,
            skills=(),
        )
        analysis_agent = create_agent(
            StructuredOutputFakeModel(),
            [],
            response_format=ToolStrategy(LeadAnalysisResult),
            context_schema=AgentExecutionContext,
            name="test_observable_lead_analysis_agent",
        )
        graph = lead_analysis_workflow_factory.compile(analysis_agent)
        accumulator = AgentStreamAccumulator()
        async for part in graph.astream(
                {
                    "messages": [{"role": "user", "content": "分析线索"}],
                    "lead": {"id": "300", "companyName": ""},
                    "customer_profile": {},
                },
                context=execution_context,
                stream_mode=["messages", "updates", "values", "custom"],
                version="v2"):
            await accumulator.consume(part)
        agent_events = [
            item for item in accumulator.events
            if item.get("metadata", {}).get("node") == "analysis_agent"
        ]
        self.assertEqual(1, len(agent_events))
        self.assertGreaterEqual(agent_events[0]["metadata"]["elapsedMs"], 0)

    async def test_lead_analysis_loads_real_lead_by_business_id(self):
        execution_context = AgentExecutionContext(
            tenant_id="100",
            user_id="200",
            scene_code="LEAD_ANALYZE",
            data_scope="SELF",
            permissions=("crm:lead:view",),
            business_type="LEAD",
            business_id="300",
            credential_key=None,
            trace_id=None,
            skills=(),
        )
        runtime = SimpleNamespace(context=execution_context)
        lead = {"id": "300", "companyName": "测试企业"}
        with patch(
                "app.workflows.lead_analysis.nodes.business_repository.lead_detail",
                new_callable=AsyncMock,
                return_value=lead) as lead_detail:
            result = await lead_analysis_nodes.prepare_context({"lead": {}}, runtime)
        lead_detail.assert_awaited_once_with("100", "200", "SELF", "300")
        self.assertEqual(lead, result["lead"])
        self.assertIn("测试企业", str(result["messages"][0].content))

    def test_runtime_context_is_not_exposed_to_model_schema(self):
        lead_schema = crm_lead_page.tool_call_schema.model_json_schema()
        skill_schema = load_skill.tool_call_schema.model_json_schema()
        self.assertNotIn("runtime", lead_schema.get("properties", {}))
        self.assertNotIn("runtime", skill_schema.get("properties", {}))
        self.assertIn("skill_code", skill_schema.get("properties", {}))

    def test_openai_compatible_reasoning_delta_is_preserved(self):
        model = OpenAICompatibleChatModel(
            model="test-model",
            api_key="test-key",
            base_url="http://localhost:9999/v1",
        )
        generation = model._convert_chunk_to_generation_chunk(
            {
                "choices": [{
                    "delta": {
                        "role": "assistant",
                        "content": "",
                        "reasoning_content": "正在分析真实业务数据",
                    },
                }],
            },
            AIMessageChunk,
            None,
        )
        self.assertIsNotNone(generation)
        self.assertEqual(
            "正在分析真实业务数据",
            generation.message.additional_kwargs.get("reasoning_content"),
        )

    def test_deepseek_v4_forced_tool_choice_disables_thinking(self):
        agent = RuntimeAgent(
            id="1",
            sceneCode="LEAD_ANALYZE",
            modelProvider="OPENAI",
            modelName="deepseek-v4-flash",
            baseUrl="https://api.deepseek.com",
            apiKey="test-key",
            extraConfigJson='{"reasoningEffort":"high","extraBody":{"custom":"value"}}',
        )
        model = chat_model_factory.build(agent, force_tool_choice=True)
        self.assertIsNone(model.reasoning_effort)
        self.assertEqual("value", model.extra_body.get("custom"))
        self.assertEqual({"type": "disabled"}, model.extra_body.get("thinking"))


if __name__ == "__main__":
    unittest.main()
