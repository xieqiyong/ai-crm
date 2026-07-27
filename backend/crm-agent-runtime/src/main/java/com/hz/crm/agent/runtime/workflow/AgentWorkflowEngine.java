package com.hz.crm.agent.runtime.workflow;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentWorkflowEngine {

    @Autowired
    private AgentWorkflowCatalog agentWorkflowCatalog;

    @Autowired(required = false)
    private List<AgentWorkflowNodeHandler> nodeHandlers = new ArrayList<AgentWorkflowNodeHandler>();

    public Flux<AgentRuntimeEvent> run(
            AgentRuntimeRequest request,
            Function<AgentRuntimeRequest, Flux<AgentRuntimeEvent>> agentRunner) {
        AgentWorkflowDefinition definition = agentWorkflowCatalog.resolve(request);
        AgentWorkflowContext context = new AgentWorkflowContext();
        context.setRequest(request);
        Flux<AgentRuntimeEvent> flux = Flux.empty();
        for (AgentWorkflowNode node : definition.getNodes()) {
            flux = flux.concatWith(resolveHandler(node).execute(node, context, agentRunner));
        }
        return flux;
    }

    private AgentWorkflowNodeHandler resolveHandler(AgentWorkflowNode node) {
        for (AgentWorkflowNodeHandler handler : safeHandlers()) {
            if (handler.supports(node.getType())) {
                return handler;
            }
        }
        throw new BusinessException("AGENT_WORKFLOW_001", "未找到流程节点处理器：" + node.getType());
    }

    private List<AgentWorkflowNodeHandler> safeHandlers() {
        if (nodeHandlers == null) {
            return new ArrayList<AgentWorkflowNodeHandler>();
        }
        return nodeHandlers;
    }
}
