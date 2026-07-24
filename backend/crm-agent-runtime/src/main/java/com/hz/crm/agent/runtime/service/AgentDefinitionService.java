package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import com.hz.crm.agent.runtime.domain.ModelConfigEntity;
import com.hz.crm.agent.runtime.dto.AgentMcpSaveRequest;
import com.hz.crm.agent.runtime.dto.AgentSaveRequest;
import com.hz.crm.agent.runtime.dto.AgentSkillSaveRequest;
import com.hz.crm.agent.runtime.repository.AgentMcpRepository;
import com.hz.crm.agent.runtime.repository.AgentRepository;
import com.hz.crm.agent.runtime.repository.AgentSkillRepository;
import com.hz.crm.agent.runtime.repository.ModelConfigRepository;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.api.PageQuery;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentDefinitionService {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentMcpRepository agentMcpRepository;

    @Autowired
    private AgentSkillRepository agentSkillRepository;

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(readOnly = true)
    public PageData<AgentEntity> page(Long tenantId, PageQuery query) {
        PageQuery safeQuery = query == null ? new PageQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AgentEntity> page = agentRepository.findByTenantIdAndDeletedFalse(tenantId, pageRequest);
        return PageData.of(page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), page.getContent());
    }

    @Transactional(readOnly = true)
    public AgentEntity detail(Long tenantId, Long id) {
        return findAgent(tenantId, id);
    }

    @Transactional
    public AgentEntity saveAgent(Long tenantId, AgentSaveRequest request) {
        if (request == null || blank(request.getName())) {
            throw new BusinessException("AGENT_004", "智能体名称不能为空");
        }
        AgentEntity entity;
        if (request.getId() == null) {
            entity = new AgentEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findAgent(tenantId, request.getId());
        }
        entity.setCode(resolveCode(entity, request));
        entity.setSceneCode(trimToNull(request.getSceneCode()));
        entity.setSceneName(trimToNull(request.getSceneName()));
        entity.setName(request.getName().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setSystemPrompt(trimToNull(request.getSystemPrompt()));
        applyModelConfig(tenantId, entity, request);
        entity.setMaxIters(resolveMaxIters(request.getMaxIters()));
        entity.setExtraConfigJson(trimToNull(request.getExtraConfigJson()));
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        validateUniqueSceneAgent(tenantId, entity);
        return agentRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public AgentEntity findEnabledByScene(Long tenantId, String sceneCode) {
        if (blank(sceneCode)) {
            return null;
        }
        List<AgentEntity> agents = agentRepository
                .findByTenantIdAndSceneCodeAndEnabledTrueAndDeletedFalseOrderByUpdatedAtDesc(
                        tenantId, sceneCode.trim());
        if (agents == null || agents.isEmpty()) {
            return null;
        }
        if (agents.size() > 1) {
            throw new BusinessException("AGENT_SCENE_005", "同一场景只能启用一个智能体：" + sceneCode.trim());
        }
        return agents.get(0);
    }

    @Transactional(readOnly = true)
    public AgentEntity findEnabledByCode(Long tenantId, String code) {
        if (blank(code)) {
            return null;
        }
        return agentRepository
                .findFirstByTenantIdAndCodeAndEnabledTrueAndDeletedFalseOrderByUpdatedAtDesc(
                        tenantId, code.trim())
                .orElse(null);
    }

    @Transactional
    public AgentMcpEntity saveMcp(Long tenantId, AgentMcpSaveRequest request) {
        if (request == null) {
            throw new BusinessException("AGENT_MCP_003", "MCP配置不能为空");
        }
        findAgent(tenantId, request.getAgentId());
        AgentMcpEntity entity;
        if (request.getId() == null) {
            entity = new AgentMcpEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setAgentId(request.getAgentId());
        } else {
            entity = findMcp(tenantId, request.getId());
            if (!request.getAgentId().equals(entity.getAgentId())) {
                throw new BusinessException("AGENT_MCP_004", "MCP配置不属于当前智能体");
            }
        }
        entity.setName(request.getName().trim());
        entity.setTransportType(request.getTransportType().trim());
        entity.setEndpoint(trimToNull(request.getEndpoint()));
        entity.setCommand(trimToNull(request.getCommand()));
        entity.setArgumentsJson(trimToNull(request.getArgumentsJson()));
        entity.setHeadersJson(trimToNull(request.getHeadersJson()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        return agentMcpRepository.save(entity);
    }

    @Transactional
    public AgentSkillEntity saveSkill(Long tenantId, AgentSkillSaveRequest request) {
        if (request == null) {
            throw new BusinessException("AGENT_SKILL_003", "Skill配置不能为空");
        }
        if (blank(request.getName())) {
            throw new BusinessException("AGENT_SKILL_005", "Skill名称不能为空");
        }
        findAgent(tenantId, request.getAgentId());
        AgentSkillEntity entity;
        if (request.getId() == null) {
            entity = new AgentSkillEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setAgentId(request.getAgentId());
        } else {
            entity = findSkill(tenantId, request.getId());
            if (!request.getAgentId().equals(entity.getAgentId())) {
                throw new BusinessException("AGENT_SKILL_004", "Skill配置不属于当前智能体");
            }
        }
        entity.setSkillKey(resolveSkillKey(entity, request));
        entity.setName(request.getName().trim());
        entity.setContent(trimToNull(request.getContent()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        return agentSkillRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AgentMcpEntity> mcps(Long tenantId, Long agentId) {
        findAgent(tenantId, agentId);
        return agentMcpRepository.findByAgentIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(agentId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<AgentSkillEntity> skills(Long tenantId, Long agentId) {
        findAgent(tenantId, agentId);
        return agentSkillRepository.findByAgentIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(agentId, tenantId);
    }

    @Transactional
    public void deleteMcp(Long tenantId, Long id) {
        AgentMcpEntity entity = findMcp(tenantId, id);
        entity.setDeleted(true);
        agentMcpRepository.save(entity);
    }

    @Transactional
    public void deleteSkill(Long tenantId, Long id) {
        AgentSkillEntity entity = findSkill(tenantId, id);
        entity.setDeleted(true);
        agentSkillRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AgentMcpEntity> enabledMcps(Long tenantId, Long agentId) {
        return agentMcpRepository.findByAgentIdAndTenantIdAndEnabledTrueAndDeletedFalse(agentId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<AgentSkillEntity> enabledSkills(Long tenantId, Long agentId) {
        return agentSkillRepository.findByAgentIdAndTenantIdAndEnabledTrueAndDeletedFalse(agentId, tenantId);
    }

    private void applyModelConfig(Long tenantId, AgentEntity entity, AgentSaveRequest request) {
        if (request.getModelConfigId() != null) {
            ModelConfigEntity config = modelConfigRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.getModelConfigId(), tenantId)
                    .orElseThrow(() -> new BusinessException("MODEL_003", "模型配置不存在"));
            if (!config.isEnabled()) {
                throw new BusinessException("MODEL_004", "模型配置已停用");
            }
            entity.setModelConfigId(config.getId());
            entity.setModelProvider(config.getProvider());
            entity.setModelName(config.getModelName());
            entity.setBaseUrl(config.getBaseUrl());
            entity.setApiKey(config.getApiKey());
            return;
        }
        if (blank(request.getModelName())) {
            throw new BusinessException("AGENT_003", "模型名称不能为空");
        }
        if (blank(request.getApiKey()) && blank(entity.getApiKey())) {
            throw new BusinessException("AGENT_006", "模型密钥不能为空");
        }
        entity.setModelConfigId(null);
        entity.setModelProvider(blank(request.getModelProvider()) ? "OPENAI" : request.getModelProvider());
        entity.setModelName(request.getModelName());
        entity.setBaseUrl(request.getBaseUrl());
        if (!blank(request.getApiKey())) {
            entity.setApiKey(request.getApiKey().trim());
        }
    }

    private String resolveCode(AgentEntity entity, AgentSaveRequest request) {
        String code = trimToNull(request.getCode());
        if (code != null) {
            return code;
        }
        if (!blank(entity.getCode())) {
            return entity.getCode();
        }
        String sceneCode = normalizeCodeToken(request.getSceneCode());
        if (!blank(sceneCode)) {
            return "agent-" + sceneCode;
        }
        return "agent-" + entity.getId();
    }

    private String resolveSkillKey(AgentSkillEntity entity, AgentSkillSaveRequest request) {
        String skillKey = normalizeCodeToken(request.getSkillKey());
        if (!blank(skillKey)) {
            return skillKey;
        }
        if (!blank(entity.getSkillKey())) {
            return entity.getSkillKey();
        }
        skillKey = normalizeCodeToken(request.getName());
        if (!blank(skillKey)) {
            return "skill-" + skillKey;
        }
        return "skill-" + entity.getId();
    }

    private int resolveMaxIters(Integer value) {
        if (value == null) {
            return 8;
        }
        if (value.intValue() < 1) {
            return 1;
        }
        if (value.intValue() > 50) {
            return 50;
        }
        return value.intValue();
    }

    private void validateUniqueSceneAgent(Long tenantId, AgentEntity entity) {
        if (entity == null || !entity.isEnabled() || blank(entity.getSceneCode())) {
            return;
        }
        List<AgentEntity> agents = agentRepository
                .findByTenantIdAndSceneCodeAndEnabledTrueAndDeletedFalseOrderByUpdatedAtDesc(
                        tenantId, entity.getSceneCode());
        if (agents == null || agents.isEmpty()) {
            return;
        }
        for (AgentEntity agent : agents) {
            if (!agent.getId().equals(entity.getId())) {
                throw new BusinessException("AGENT_SCENE_006", "该场景已存在启用的智能体，请先停用原智能体");
            }
        }
    }

    private String normalizeCodeToken(String value) {
        if (blank(value)) {
            return null;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        text = text.replaceAll("[^a-z0-9]+", "-");
        while (text.startsWith("-")) {
            text = text.substring(1);
        }
        while (text.endsWith("-")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private AgentEntity findAgent(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("AGENT_001", "Agent编号不能为空");
        }
        return agentRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("AGENT_002", "Agent不存在"));
    }

    private AgentMcpEntity findMcp(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("AGENT_MCP_001", "MCP配置编号不能为空");
        }
        return agentMcpRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("AGENT_MCP_002", "MCP配置不存在"));
    }

    private AgentSkillEntity findSkill(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("AGENT_SKILL_001", "Skill配置编号不能为空");
        }
        return agentSkillRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("AGENT_SKILL_002", "Skill配置不存在"));
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private String trimToNull(String value) {
        if (blank(value)) {
            return null;
        }
        return value.trim();
    }
}
