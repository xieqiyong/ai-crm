package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeWorkspaceService {

    @Autowired
    private AgentRuntimeProperties agentRuntimeProperties;

    public Path resolveAgentWorkspace(AgentRuntimeRequest request) {
        return Paths.get(
                agentRuntimeProperties.getWorkspace(),
                safePath(String.valueOf(request.getTenantId())),
                String.valueOf(request.getAgent().getId()));
    }

    private String safePath(String value) {
        if (value == null || value.trim().length() == 0) {
            return "default";
        }
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
