package com.hz.crm.agent.web.tool;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.service.AgentRuntimeToolProvider;
import io.agentscope.core.tool.AgentTool;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChannelAnalysisResultToolProvider implements AgentRuntimeToolProvider {

    private static final String CHANNEL_ANALYZE_SCENE = "CHANNEL_ANALYZE";

    @Autowired
    private ChannelAnalysisResultTool channelAnalysisResultTool;

    @Autowired
    private CompanyWebSearchTool companyWebSearchTool;

    @Autowired
    private KnowledgeSearchTool knowledgeSearchTool;

    @Override
    public List<AgentTool> resolveTools(AgentRuntimeRequest request) {
        List<AgentTool> tools = new ArrayList<AgentTool>();
        if (request == null || !CHANNEL_ANALYZE_SCENE.equals(request.getSceneCode())) {
            return tools;
        }
        tools.add(companyWebSearchTool);
        tools.add(knowledgeSearchTool.bind(request));
        tools.add(channelAnalysisResultTool.bind(request));
        return tools;
    }
}
