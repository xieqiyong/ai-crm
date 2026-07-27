package com.hz.crm.agent.runtime.workflow;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import java.util.function.Function;
import reactor.core.publisher.Flux;

public interface AgentWorkflowNodeHandler {

    boolean supports(String type);

    Flux<AgentRuntimeEvent> execute(
            AgentWorkflowNode node,
            AgentWorkflowContext context,
            Function<AgentRuntimeRequest, Flux<AgentRuntimeEvent>> agentRunner);
}
