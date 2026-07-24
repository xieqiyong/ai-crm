package com.hz.crm.agent.runtime.core;

import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import reactor.core.publisher.Flux;

public interface AgentRuntimeEngine {

    Flux<AgentRuntimeEvent> run(AgentRuntimeRequest request);
}
