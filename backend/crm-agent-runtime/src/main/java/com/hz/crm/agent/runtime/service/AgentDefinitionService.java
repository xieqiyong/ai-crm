package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import com.hz.crm.agent.runtime.dto.AgentMcpSaveRequest;
import com.hz.crm.agent.runtime.dto.AgentSaveRequest;
import com.hz.crm.agent.runtime.dto.AgentSkillSaveRequest;
import com.hz.crm.agent.runtime.repository.AgentMcpRepository;
import com.hz.crm.agent.runtime.repository.AgentRepository;
import com.hz.crm.agent.runtime.repository.AgentSkillRepository;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.api.PageQuery;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import java.util.List;
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
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(readOnly = true)
    public PageData<AgentEntity> page(String tenantId, PageQuery query) {
        PageQuery safeQuery = query == null ? new PageQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AgentEntity> page = agentRepository.findByTenantIdAndDeletedFalse(tenantId, pageRequest);
        return PageData.of(page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), page.getContent());
    }

    @Transactional(readOnly = true)
    public AgentEntity detail(String tenantId, Long id) {
        return findAgent(tenantId, id);
    }

    @Transactional
    public AgentEntity saveAgent(String tenantId, AgentSaveRequest request) {
        AgentEntity entity;
        if (request.getId() == null) {
            entity = new AgentEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findAgent(tenantId, request.getId());
        }
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setSystemPrompt(request.getSystemPrompt());
        entity.setModelProvider(request.getModelProvider() == null ? "OPENAI" : request.getModelProvider());
        entity.setModelName(request.getModelName());
        entity.setBaseUrl(request.getBaseUrl());
        entity.setApiKeyEnv(request.getApiKeyEnv());
        entity.setMaxIters(request.getMaxIters() == null ? 8 : request.getMaxIters());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        return agentRepository.save(entity);
    }

    @Transactional
    public AgentMcpEntity saveMcp(String tenantId, AgentMcpSaveRequest request) {
        findAgent(tenantId, request.getAgentId());
        AgentMcpEntity entity;
        if (request.getId() == null) {
            entity = new AgentMcpEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setAgentId(request.getAgentId());
        } else {
            entity = agentMcpRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.getId(), tenantId)
                    .orElseThrow(() -> new BusinessException("AGENT_MCP_001", "MCP配置不存在"));
        }
        entity.setName(request.getName());
        entity.setTransportType(request.getTransportType());
        entity.setEndpoint(request.getEndpoint());
        entity.setCommand(request.getCommand());
        entity.setArgumentsJson(request.getArgumentsJson());
        entity.setHeadersJson(request.getHeadersJson());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        return agentMcpRepository.save(entity);
    }

    @Transactional
    public AgentSkillEntity saveSkill(String tenantId, AgentSkillSaveRequest request) {
        findAgent(tenantId, request.getAgentId());
        AgentSkillEntity entity;
        if (request.getId() == null) {
            entity = new AgentSkillEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setAgentId(request.getAgentId());
        } else {
            entity = agentSkillRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.getId(), tenantId)
                    .orElseThrow(() -> new BusinessException("AGENT_SKILL_001", "Skill配置不存在"));
        }
        entity.setSkillKey(request.getSkillKey());
        entity.setName(request.getName());
        entity.setContent(request.getContent());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        return agentSkillRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AgentMcpEntity> enabledMcps(String tenantId, Long agentId) {
        return agentMcpRepository.findByAgentIdAndTenantIdAndEnabledTrueAndDeletedFalse(agentId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<AgentSkillEntity> enabledSkills(String tenantId, Long agentId) {
        return agentSkillRepository.findByAgentIdAndTenantIdAndEnabledTrueAndDeletedFalse(agentId, tenantId);
    }

    private AgentEntity findAgent(String tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("AGENT_001", "Agent编号不能为空");
        }
        return agentRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("AGENT_002", "Agent不存在"));
    }
}
