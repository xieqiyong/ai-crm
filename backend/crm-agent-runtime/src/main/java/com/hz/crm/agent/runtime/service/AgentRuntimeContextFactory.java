package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import io.agentscope.core.agent.RuntimeContext;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeContextFactory {

    public RuntimeContext build(AgentRuntimeRequest request) {
        RuntimeContext context = RuntimeContext.builder()
                .sessionId(resolveSessionId(request.getSessionId()))
                .userId(String.valueOf(request.getUserId()))
                .put("tenantId", String.valueOf(request.getTenantId()))
                .put("agentId", String.valueOf(request.getAgent().getId()))
                .build();
        if (request.getContext() != null) {
            for (Map.Entry<String, Object> entry : request.getContext().entrySet()) {
                context.put(entry.getKey(), entry.getValue());
            }
        }
        if (request.getRunId() != null) {
            context.put("runId", String.valueOf(request.getRunId()));
        }
        if (request.getConversationId() != null) {
            context.put("conversationId", String.valueOf(request.getConversationId()));
        }
        return context;
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().length() == 0) {
            return "default";
        }
        return sessionId.trim();
    }

}
