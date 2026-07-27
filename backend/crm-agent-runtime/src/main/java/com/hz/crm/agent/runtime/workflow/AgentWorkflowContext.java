package com.hz.crm.agent.runtime.workflow;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentWorkflowContext {

    private AgentRuntimeRequest request;

    private Map<String, Object> attributes = new HashMap<String, Object>();
}
