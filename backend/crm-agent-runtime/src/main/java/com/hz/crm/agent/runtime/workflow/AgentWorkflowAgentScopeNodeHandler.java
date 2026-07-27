package com.hz.crm.agent.runtime.workflow;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentWorkflowAgentScopeNodeHandler implements AgentWorkflowNodeHandler {

    @Override
    public boolean supports(String type) {
        return AgentWorkflowNodeType.AGENT_SCOPE.equals(type);
    }

    @Override
    public Flux<AgentRuntimeEvent> execute(
            AgentWorkflowNode node,
            AgentWorkflowContext context,
            Function<AgentRuntimeRequest, Flux<AgentRuntimeEvent>> agentRunner) {
        return agentRunner.apply(context.getRequest());
    }
}
