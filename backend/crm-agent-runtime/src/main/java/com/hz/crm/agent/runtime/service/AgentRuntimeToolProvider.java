package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import io.agentscope.core.tool.AgentTool;
import java.util.List;

public interface AgentRuntimeToolProvider {

    List<AgentTool> resolveTools(AgentRuntimeRequest request);
}
