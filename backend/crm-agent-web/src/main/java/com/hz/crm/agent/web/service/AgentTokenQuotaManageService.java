package com.hz.crm.agent.web.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.agent.runtime.domain.AgentTokenQuotaUserEntity;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaAssignRequest;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaClearRequest;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaDepartmentOption;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaOverviewResponse;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaUserOption;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaUserResponse;
import com.hz.crm.agent.runtime.mapper.AgentTokenQuotaUserMapper;
import com.hz.crm.agent.runtime.service.AgentTokenQuotaService;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTokenQuotaManageService {

    @Autowired
    private AgentTokenQuotaUserMapper agentTokenQuotaUserMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private AgentTokenQuotaService agentTokenQuotaService;

    @Transactional(readOnly = true)
    public AgentTokenQuotaOverviewResponse overview(Long tenantId) {
        List<AgentTokenQuotaUserOption> users = agentTokenQuotaUserMapper.users(tenantId);
        List<AgentTokenQuotaDepartmentOption> departments = agentTokenQuotaUserMapper.departments(tenantId);
        List<AgentTokenQuotaUserEntity> quotas = agentTokenQuotaUserMapper.selectList(
                Wrappers.<AgentTokenQuotaUserEntity>lambdaQuery()
                        .eq(AgentTokenQuotaUserEntity::getTenantId, tenantId)
                        .eq(AgentTokenQuotaUserEntity::isDeleted, false)
                        .orderByDesc(AgentTokenQuotaUserEntity::getUpdatedAt));
        AgentTokenQuotaOverviewResponse response = new AgentTokenQuotaOverviewResponse();
        response.setDefaultDailyTokenLimit(agentTokenQuotaService.resolveDefaultDailyLimit());
        response.setUsers(users);
        response.setDepartments(departments);
        response.setQuotas(toResponses(quotas, users));
        return response;
    }

    @Transactional
    public AgentTokenQuotaOverviewResponse assign(Long tenantId, AgentTokenQuotaAssignRequest request) {
        if (request == null || request.getDailyTokenLimit() == null || request.getDailyTokenLimit() < 0L) {
            throw new BusinessException("AGENT_TOKEN_QUOTA_001", "Token额度不能小于0");
        }
        List<AgentTokenQuotaUserOption> users = agentTokenQuotaUserMapper.users(tenantId);
        List<AgentTokenQuotaDepartmentOption> departments = agentTokenQuotaUserMapper.departments(tenantId);
        List<AgentTokenQuotaUserOption> targets = resolveTargetUsers(request, users);
        if (targets.isEmpty()) {
            throw new BusinessException("AGENT_TOKEN_QUOTA_002", "没有匹配到需要设置额度的用户");
        }
        String scope = resolveScope(request.getScope());
        String targetName = resolveTargetName(scope, request, users, departments);
        Long targetId = "DEPARTMENT".equals(scope) ? request.getDepartmentId() : null;
        for (AgentTokenQuotaUserOption user : targets) {
            saveUserQuota(tenantId, user, request, scope, targetId, targetName);
        }
        return overview(tenantId);
    }

    @Transactional
    public AgentTokenQuotaOverviewResponse clear(Long tenantId, AgentTokenQuotaClearRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new BusinessException("AGENT_TOKEN_QUOTA_003", "用户不能为空");
        }
        AgentTokenQuotaUserEntity entity = findByUserId(tenantId, request.getUserId());
        if (entity != null) {
            entity.setDeleted(true);
            entity.setUpdatedAt(DateTimes.now());
            agentTokenQuotaUserMapper.updateById(entity);
        }
        return overview(tenantId);
    }

    private List<AgentTokenQuotaUserOption> resolveTargetUsers(
            AgentTokenQuotaAssignRequest request,
            List<AgentTokenQuotaUserOption> users) {
        String scope = resolveScope(request.getScope());
        List<AgentTokenQuotaUserOption> targets = new ArrayList<AgentTokenQuotaUserOption>();
        if ("COMPANY".equals(scope) || "ALL".equals(scope)) {
            targets.addAll(users);
            return targets;
        }
        if ("DEPARTMENT".equals(scope)) {
            if (request.getDepartmentId() == null) {
                throw new BusinessException("AGENT_TOKEN_QUOTA_004", "部门不能为空");
            }
            for (AgentTokenQuotaUserOption user : users) {
                if (request.getDepartmentId().equals(user.getDepartmentId())) {
                    targets.add(user);
                }
            }
            return targets;
        }
        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new BusinessException("AGENT_TOKEN_QUOTA_005", "用户不能为空");
        }
        for (AgentTokenQuotaUserOption user : users) {
            if (request.getUserIds().contains(user.getId())) {
                targets.add(user);
            }
        }
        return targets;
    }

    private void saveUserQuota(
            Long tenantId,
            AgentTokenQuotaUserOption user,
            AgentTokenQuotaAssignRequest request,
            String scope,
            Long targetId,
            String targetName) {
        AgentTokenQuotaUserEntity entity = findByUserId(tenantId, user.getId());
        LocalDateTime now = DateTimes.now();
        if (entity == null) {
            entity = new AgentTokenQuotaUserEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setUserId(user.getId());
            entity.setCreatedAt(now);
        }
        entity.setDailyTokenLimit(request.getDailyTokenLimit());
        entity.setAssignScope(scope);
        entity.setAssignTargetId(targetId);
        entity.setAssignTargetName(targetName);
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setDeleted(false);
        entity.setUpdatedAt(now);
        if (agentTokenQuotaUserMapper.selectById(entity.getId()) == null) {
            agentTokenQuotaUserMapper.insert(entity);
        } else {
            agentTokenQuotaUserMapper.updateById(entity);
        }
    }

    private AgentTokenQuotaUserEntity findByUserId(Long tenantId, Long userId) {
        return agentTokenQuotaUserMapper.selectOne(Wrappers.<AgentTokenQuotaUserEntity>lambdaQuery()
                .eq(AgentTokenQuotaUserEntity::getTenantId, tenantId)
                .eq(AgentTokenQuotaUserEntity::getUserId, userId)
                .last("limit 1"));
    }

    private List<AgentTokenQuotaUserResponse> toResponses(
            List<AgentTokenQuotaUserEntity> quotas,
            List<AgentTokenQuotaUserOption> users) {
        Map<Long, AgentTokenQuotaUserOption> userMap = new HashMap<Long, AgentTokenQuotaUserOption>();
        for (AgentTokenQuotaUserOption user : users) {
            userMap.put(user.getId(), user);
        }
        List<AgentTokenQuotaUserResponse> responses = new ArrayList<AgentTokenQuotaUserResponse>();
        for (AgentTokenQuotaUserEntity quota : quotas) {
            AgentTokenQuotaUserOption user = userMap.get(quota.getUserId());
            AgentTokenQuotaUserResponse response = new AgentTokenQuotaUserResponse();
            response.setId(quota.getId());
            response.setUserId(quota.getUserId());
            if (user != null) {
                response.setUsername(user.getUsername());
                response.setDisplayName(user.getDisplayName());
                response.setDepartmentId(user.getDepartmentId());
                response.setDepartmentName(user.getDepartmentName());
            }
            response.setDailyTokenLimit(quota.getDailyTokenLimit());
            response.setAssignScope(quota.getAssignScope());
            response.setAssignTargetId(quota.getAssignTargetId());
            response.setAssignTargetName(quota.getAssignTargetName());
            response.setRemark(quota.getRemark());
            response.setEnabled(quota.isEnabled());
            response.setUpdatedAt(quota.getUpdatedAt());
            responses.add(response);
        }
        return responses;
    }

    private String resolveTargetName(
            String scope,
            AgentTokenQuotaAssignRequest request,
            List<AgentTokenQuotaUserOption> users,
            List<AgentTokenQuotaDepartmentOption> departments) {
        if ("COMPANY".equals(scope) || "ALL".equals(scope)) {
            return "全公司";
        }
        if ("DEPARTMENT".equals(scope)) {
            for (AgentTokenQuotaDepartmentOption department : departments) {
                if (department.getId().equals(request.getDepartmentId())) {
                    return department.getName();
                }
            }
            return "指定部门";
        }
        if (request.getUserIds() != null && request.getUserIds().size() == 1) {
            Long userId = request.getUserIds().get(0);
            for (AgentTokenQuotaUserOption user : users) {
                if (user.getId().equals(userId)) {
                    return blank(user.getDisplayName()) ? user.getUsername() : user.getDisplayName();
                }
            }
        }
        return "指定用户";
    }

    private String resolveScope(String value) {
        if (blank(value)) {
            return "USER";
        }
        String scope = value.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(scope)) {
            return "COMPANY";
        }
        if (!"USER".equals(scope) && !"DEPARTMENT".equals(scope) && !"COMPANY".equals(scope)) {
            throw new BusinessException("AGENT_TOKEN_QUOTA_006", "额度范围不正确");
        }
        return scope;
    }

    private String trimToNull(String value) {
        if (blank(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
