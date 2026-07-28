package com.hz.crm.agent.web.tool;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.service.AgentRuntimeToolProvider;
import io.agentscope.core.tool.AgentTool;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MarketingAssistantToolProvider implements AgentRuntimeToolProvider {

    private static final String GENERAL_ASSISTANT_SCENE = "GENERAL_ASSISTANT";

    @Autowired
    private KnowledgeSearchTool knowledgeSearchTool;

    @Override
    public List<AgentTool> resolveTools(AgentRuntimeRequest request) {
        List<AgentTool> tools = new ArrayList<AgentTool>();
        if (request == null || !GENERAL_ASSISTANT_SCENE.equals(request.getSceneCode())) {
            return tools;
        }
        tools.add(knowledgeSearchTool.bind(request));
        return tools;
    }
}
