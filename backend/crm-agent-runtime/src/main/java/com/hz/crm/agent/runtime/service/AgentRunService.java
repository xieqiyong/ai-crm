package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import com.hz.crm.agent.runtime.dto.AgentRunRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.common.exception.BusinessException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentRunService {

    @Autowired
    private AgentDefinitionService agentDefinitionService;

    @Autowired
    private AgentScopeRuntime agentScopeRuntime;

    public Flux<AgentRuntimeEvent> run(String tenantId, Long userId, AgentRunRequest request) {
        AgentEntity agent = agentDefinitionService.detail(tenantId, request.getAgentId());
        if (!agent.isEnabled()) {
            return Flux.error(new BusinessException("AGENT_003", "Agent已停用"));
        }
        List<AgentMcpEntity> mcps = agentDefinitionService.enabledMcps(tenantId, request.getAgentId());
        List<AgentSkillEntity> skills = agentDefinitionService.enabledSkills(tenantId, request.getAgentId());
        return agentScopeRuntime.run(tenantId, userId, agent, mcps, skills, request);
    }
}
