package com.hz.crm.agent.runtime.workflow;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowCatalog {

    private static final String LEAD_ANALYZE_SCENE = "LEAD_ANALYZE";

    private static final String CUSTOMER_DEEP_SUMMARY_SCENE = "CUSTOMER_DEEP_SUMMARY";

    public AgentWorkflowDefinition resolve(AgentRuntimeRequest request) {
        String sceneCode = request == null ? "" : trimToEmpty(request.getSceneCode()).toUpperCase();
        if (LEAD_ANALYZE_SCENE.equals(sceneCode)) {
            return leadAnalyzeWorkflow();
        }
        if (CUSTOMER_DEEP_SUMMARY_SCENE.equals(sceneCode)) {
            return customerDeepSummaryWorkflow();
        }
        return defaultWorkflow(sceneCode);
    }

    private AgentWorkflowDefinition leadAnalyzeWorkflow() {
        AgentWorkflowDefinition definition = new AgentWorkflowDefinition();
        definition.setCode("LEAD_ANALYZE_WORKFLOW");
        definition.setName("线索AI分析流程");
        definition.addNode(AgentWorkflowNode.of("PREPARE_CONTEXT", "读取线索上下文", AgentWorkflowNodeType.EVENT));
        definition.addNode(AgentWorkflowNode.of("PREPARE_TOOLS", "准备检索和结构化工具", AgentWorkflowNodeType.EVENT));
        definition.addNode(AgentWorkflowNode.of("RUN_AGENT", "执行智能体分析", AgentWorkflowNodeType.AGENT_SCOPE));
        definition.addNode(AgentWorkflowNode.of("FINALIZE_RESULT", "整理分析结果", AgentWorkflowNodeType.EVENT));
        return definition;
    }

    private AgentWorkflowDefinition customerDeepSummaryWorkflow() {
        AgentWorkflowDefinition definition = new AgentWorkflowDefinition();
        definition.setCode("CUSTOMER_DEEP_SUMMARY_WORKFLOW");
        definition.setName("客户深度总结流程");
        definition.addNode(AgentWorkflowNode.of("PREPARE_CONTEXT", "读取客户上下文", AgentWorkflowNodeType.EVENT));
        definition.addNode(AgentWorkflowNode.of("PREPARE_TOOLS", "准备知识库和结构化工具", AgentWorkflowNodeType.EVENT));
        definition.addNode(AgentWorkflowNode.of("RUN_AGENT", "执行客户总结", AgentWorkflowNodeType.AGENT_SCOPE));
        definition.addNode(AgentWorkflowNode.of("FINALIZE_RESULT", "保存客户总结", AgentWorkflowNodeType.EVENT));
        return definition;
    }

    private AgentWorkflowDefinition defaultWorkflow(String sceneCode) {
        AgentWorkflowDefinition definition = new AgentWorkflowDefinition();
        definition.setCode(trimToEmpty(sceneCode) + "_WORKFLOW");
        definition.setName("默认智能体流程");
        definition.addNode(AgentWorkflowNode.of("RUN_AGENT", "执行智能体", AgentWorkflowNodeType.AGENT_SCOPE));
        return definition;
    }

    private String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
