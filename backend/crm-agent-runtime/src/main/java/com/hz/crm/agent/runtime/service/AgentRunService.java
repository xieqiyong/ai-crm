package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.dto.AgentRunRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentRunService {

    @Autowired
    private AgentDefinitionService agentDefinitionService;

    @Autowired
    private AgentRuntimeFacade agentRuntimeFacade;

    public Flux<AgentRuntimeEvent> run(Long tenantId, Long userId, AgentRunRequest request) {
        if (request == null) {
            return Flux.error(new BusinessException("AGENT_001", "Agent运行请求不能为空"));
        }
        AgentEntity agent = agentDefinitionService.detail(tenantId, request.getAgentId());
        if (!agent.isEnabled()) {
            return Flux.error(new BusinessException("AGENT_003", "Agent已停用"));
        }
        AgentRuntimeRequest runtimeRequest = new AgentRuntimeRequest();
        runtimeRequest.setTenantId(tenantId);
        runtimeRequest.setUserId(userId);
        runtimeRequest.setAgent(agent);
        runtimeRequest.setMessage(request.getMessage());
        runtimeRequest.setSessionId(request.getSessionId());
        runtimeRequest.setInjectedPrompt(request.getInjectedPrompt());
        runtimeRequest.setSceneCode(resolveSceneCode(agent, request));
        runtimeRequest.setBusinessType(request.getBusinessType());
        runtimeRequest.setBusinessId(request.getBusinessId());
        runtimeRequest.setContext(request.getContext());
        return agentRuntimeFacade.run(runtimeRequest);
    }

    private String resolveSceneCode(AgentEntity agent, AgentRunRequest request) {
        if (request.getSceneCode() != null && request.getSceneCode().trim().length() > 0) {
            return request.getSceneCode().trim();
        }
        return agent.getSceneCode();
    }
}
