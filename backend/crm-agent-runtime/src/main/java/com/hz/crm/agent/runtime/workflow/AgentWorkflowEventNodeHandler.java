package com.hz.crm.agent.runtime.workflow;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentWorkflowEventNodeHandler implements AgentWorkflowNodeHandler {

    @Autowired
    private AgentWorkflowEventFactory agentWorkflowEventFactory;

    @Override
    public boolean supports(String type) {
        return AgentWorkflowNodeType.EVENT.equals(type);
    }

    @Override
    public Flux<AgentRuntimeEvent> execute(
            AgentWorkflowNode node,
            AgentWorkflowContext context,
            Function<AgentRuntimeRequest, Flux<AgentRuntimeEvent>> agentRunner) {
        return Flux.just(agentWorkflowEventFactory.success(node, context.getRequest()));
    }
}
