package com.hz.crm.agent.web.tool;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.common.exception.BusinessException;
import io.agentscope.core.tool.AgentTool;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CrmBusinessQueryToolCatalog {

    @Autowired
    private LeadQueryTool leadQueryTool;

    @Autowired
    private CustomerQueryTool customerQueryTool;

    @Autowired
    private FollowupQueryTool followupQueryTool;

    public List<String> supportedToolNames() {
        List<String> names = new ArrayList<String>();
        names.add(LeadQueryTool.TOOL_NAME);
        names.add(CustomerQueryTool.TOOL_NAME);
        names.add(FollowupQueryTool.TOOL_NAME);
        return names;
    }

    public List<AgentTool> bind(
            AgentRuntimeRequest request, List<String> toolNames) {
        List<AgentTool> tools = new ArrayList<AgentTool>();
        if (toolNames == null || toolNames.isEmpty()) {
            return tools;
        }
        Set<String> mounted = new HashSet<String>();
        for (String toolName : toolNames) {
            if (toolName == null || !mounted.add(toolName)) {
                continue;
            }
            tools.add(bindOne(request, toolName));
        }
        return tools;
    }

    public AgentTool bindOne(AgentRuntimeRequest request, String toolName) {
        if (LeadQueryTool.TOOL_NAME.equals(toolName)) {
            return leadQueryTool.bind(request);
        }
        if (CustomerQueryTool.TOOL_NAME.equals(toolName)) {
            return customerQueryTool.bind(request);
        }
        if (FollowupQueryTool.TOOL_NAME.equals(toolName)) {
            return followupQueryTool.bind(request);
        }
        throw new BusinessException("AGENT_TOOL_001", "不支持的CRM业务查询工具：" + toolName);
    }
}
