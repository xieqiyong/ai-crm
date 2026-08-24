from langgraph.graph import END, START, StateGraph

from app.agents.factory import tool_calling_agent_factory
from app.runtime.bundle import RuntimeGraphBundle
from app.runtime.context import RuntimeContext
from app.runtime.execution_context import AgentExecutionContext
from app.schemas.runtime import RuntimeRunRequest
from app.services.web_search import empty_profile
from app.workflows.lead_analysis.nodes import lead_analysis_nodes
from app.workflows.lead_analysis.state import LeadAnalysisState
from app.workflows.node_observability import observable_node


class LeadAnalysisWorkflowFactory:
    async def build(
            self,
            request: RuntimeRunRequest,
            runtime_context: RuntimeContext,
            checkpointer=None) -> RuntimeGraphBundle:
        agent_bundle = await tool_calling_agent_factory.build(
            request,
            runtime_context,
            checkpointer=None,
            excluded_tool_names={"customer_web_search"},
            workflow_prompt=(
                "客户公开信息检索由外层线索分析Workflow负责，检索结果会作为当前消息提供。"
                "你可以按需调用知识库和其他已授权工具补充证据，最终必须提交LeadAnalysisResult结构化结果。"
            ),
        )
        graph = self.compile(agent_bundle.graph, checkpointer)
        lead = request.context.get("lead")
        if not isinstance(lead, dict):
            lead = {}
        return RuntimeGraphBundle(
            graph=graph,
            execution_context=agent_bundle.execution_context,
            mcp_bundle=agent_bundle.mcp_bundle,
            input_state={
                "messages": [{
                    "role": "user",
                    "content": tool_calling_agent_factory.user_message(request),
                }],
                "lead": lead,
                "customer_profile": empty_profile(lead_analysis_nodes.company_name(lead)),
            },
        )

    def compile(self, analysis_agent, checkpointer=None):
        builder = StateGraph(LeadAnalysisState, context_schema=AgentExecutionContext)
        builder.add_node(
            "prepare_context",
            observable_node(
                "prepare_context",
                "读取线索上下文",
            )(lead_analysis_nodes.prepare_context),
        )
        builder.add_node(
            "company_web_search",
            observable_node(
                "company_web_search",
                "检索客户公开信息",
            )(lead_analysis_nodes.company_web_search),
        )
        builder.add_node("analysis_agent", analysis_agent)
        builder.add_node(
            "validate_output",
            observable_node(
                "validate_output",
                "校验结构化结果",
            )(lead_analysis_nodes.validate_output),
        )
        builder.add_node(
            "finalize_result",
            observable_node(
                "finalize_result",
                "整理线索分析结果",
            )(lead_analysis_nodes.finalize_result),
        )
        builder.add_edge(START, "prepare_context")
        builder.add_conditional_edges(
            "prepare_context",
            lead_analysis_nodes.route_after_prepare,
            {
                "company_web_search": "company_web_search",
                "analysis_agent": "analysis_agent",
            },
        )
        builder.add_edge("company_web_search", "analysis_agent")
        builder.add_edge("analysis_agent", "validate_output")
        builder.add_edge("validate_output", "finalize_result")
        builder.add_edge("finalize_result", END)
        return builder.compile(checkpointer=checkpointer)


lead_analysis_workflow_factory = LeadAnalysisWorkflowFactory()
