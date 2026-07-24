package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import com.hz.crm.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRuntimeSceneService {

    @Autowired
    private AgentDefinitionService agentDefinitionService;

    @Transactional(readOnly = true)
    public AgentEntity prepare(AgentRuntimeRequest request) {
        if (request == null) {
            throw new BusinessException("AGENT_SCENE_001", "运行请求不能为空");
        }
        if (request.getTenantId() == null) {
            throw new BusinessException("AGENT_SCENE_007", "租户不能为空");
        }
        String sceneCode = resolveSceneCode(request);
        AgentEntity sceneAgent = agentDefinitionService.findEnabledByScene(request.getTenantId(), sceneCode);
        if (sceneAgent == null) {
            throw new BusinessException("AGENT_SCENE_002", "场景智能体未配置或已停用：" + sceneCode);
        }
        validateAgentMatch(request, sceneAgent);
        List<AgentMcpEntity> mcps = agentDefinitionService.enabledMcps(request.getTenantId(), sceneAgent.getId());
        List<AgentSkillEntity> skills = agentDefinitionService.enabledSkills(request.getTenantId(), sceneAgent.getId());
        request.setSceneCode(sceneAgent.getSceneCode());
        request.setAgent(sceneAgent);
        request.setMcps(mcps == null ? new ArrayList<AgentMcpEntity>() : mcps);
        request.setSkills(skills == null ? new ArrayList<AgentSkillEntity>() : skills);
        fillSceneContext(request, sceneAgent);
        return sceneAgent;
    }

    private String resolveSceneCode(AgentRuntimeRequest request) {
        if (!blank(request.getSceneCode())) {
            return request.getSceneCode().trim();
        }
        if (request.getAgent() != null && !blank(request.getAgent().getSceneCode())) {
            return request.getAgent().getSceneCode().trim();
        }
        throw new BusinessException("AGENT_SCENE_003", "场景编码不能为空");
    }

    private void validateAgentMatch(AgentRuntimeRequest request, AgentEntity sceneAgent) {
        AgentEntity requestAgent = request.getAgent();
        if (requestAgent == null || requestAgent.getId() == null) {
            return;
        }
        if (!requestAgent.getId().equals(sceneAgent.getId())) {
            throw new BusinessException("AGENT_SCENE_004", "运行智能体必须匹配场景智能体");
        }
    }

    private void fillSceneContext(AgentRuntimeRequest request, AgentEntity sceneAgent) {
        Map<String, Object> context = request.getContext();
        if (context == null) {
            context = new HashMap<String, Object>();
            request.setContext(context);
        }
        context.put("sceneCode", sceneAgent.getSceneCode());
        context.put("sceneName", sceneAgent.getSceneName());
        context.put("agentId", String.valueOf(sceneAgent.getId()));
        context.put("agentName", sceneAgent.getName());
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
