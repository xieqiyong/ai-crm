package com.hz.crm.wecom.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.wecom.WecomCorpConfigEntity;
import com.hz.crm.domain.wecom.WecomSyncTaskEntity;
import com.hz.crm.domain.wecom.WecomUserBindingEntity;
import com.hz.crm.domain.wecom.mapper.WecomCorpConfigMapper;
import com.hz.crm.domain.wecom.mapper.WecomSyncTaskMapper;
import com.hz.crm.domain.wecom.mapper.WecomUserBindingMapper;
import com.hz.crm.wecom.dto.WecomBindingResponse;
import com.hz.crm.wecom.dto.WecomBindingSaveItem;
import com.hz.crm.wecom.dto.WecomBindingSaveRequest;
import com.hz.crm.wecom.dto.WecomConfigResponse;
import com.hz.crm.wecom.dto.WecomConfigSaveRequest;
import com.hz.crm.wecom.dto.WecomSyncTaskResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WecomManageService {

    @Autowired
    private WecomCorpConfigMapper configMapper;

    @Autowired
    private WecomUserBindingMapper bindingMapper;

    @Autowired
    private WecomSyncTaskMapper taskMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private WecomTokenService tokenService;

    @Transactional(readOnly = true)
    public WecomConfigResponse detail(Long tenantId) {
        WecomCorpConfigEntity config = findFirstConfig(tenantId);
        return config == null ? null : toConfigResponse(config);
    }

    @Transactional
    public WecomConfigResponse save(Long tenantId, WecomConfigSaveRequest request) {
        validateConfig(request);
        WecomCorpConfigEntity config = request.getId() == null
                ? findFirstConfig(tenantId)
                : findConfig(tenantId, request.getId());
        LocalDateTime now = DateTimes.now();
        if (config == null) {
            config = new WecomCorpConfigEntity();
            config.setId(snowflakeIdGenerator.nextId());
            config.setTenantId(tenantId);
            config.setDeleted(false);
            config.setCreatedAt(now);
        }
        String previousCorpId = config.getCorpId();
        config.setName(request.getName().trim());
        config.setCorpId(request.getCorpId().trim());
        if (StringUtils.hasText(request.getCorpSecret())) {
            config.setCorpSecret(request.getCorpSecret().trim());
        }
        if (!StringUtils.hasText(config.getCorpSecret())) {
            throw new BusinessException("WECOM_CONFIG_002", "首次配置必须填写客户联系Secret");
        }
        config.setEnabled(request.isEnabled());
        config.setSyncIntervalMinutes(normalizeInterval(request.getSyncIntervalMinutes()));
        config.setDefaultOwnerId(request.getDefaultOwnerId());
        config.setUpdatedAt(now);
        if (configMapper.selectById(config.getId()) == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        if (!config.getCorpId().equals(previousCorpId) || StringUtils.hasText(request.getCorpSecret())) {
            tokenService.clearToken(tenantId, config.getId());
        }
        return toConfigResponse(config);
    }

    @Transactional(readOnly = true)
    public List<WecomBindingResponse> listBindings(Long tenantId, Long configId) {
        findConfig(tenantId, configId);
        QueryWrapper<WecomUserBindingEntity> wrapper = new QueryWrapper<WecomUserBindingEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("deleted", false);
        wrapper.orderByAsc("wecom_user_id");
        List<WecomUserBindingEntity> entities = bindingMapper.selectList(wrapper);
        List<WecomBindingResponse> responses = new ArrayList<WecomBindingResponse>();
        for (WecomUserBindingEntity entity : entities) {
            responses.add(toBindingResponse(entity));
        }
        return responses;
    }

    @Transactional
    public List<WecomBindingResponse> saveBindings(Long tenantId, WecomBindingSaveRequest request) {
        if (request == null || request.getConfigId() == null) {
            throw new BusinessException("WECOM_BINDING_001", "企业微信配置编号不能为空");
        }
        findConfig(tenantId, request.getConfigId());
        List<WecomBindingSaveItem> items = request.getBindings() == null
                ? Collections.<WecomBindingSaveItem>emptyList()
                : request.getBindings();
        LocalDateTime now = DateTimes.now();
        for (WecomBindingSaveItem item : items) {
            if (item == null || !StringUtils.hasText(item.getWecomUserId())) {
                continue;
            }
            WecomUserBindingEntity binding = findBinding(
                    tenantId, request.getConfigId(), item.getWecomUserId().trim());
            if (binding == null) {
                binding = new WecomUserBindingEntity();
                binding.setId(snowflakeIdGenerator.nextId());
                binding.setTenantId(tenantId);
                binding.setConfigId(request.getConfigId());
                binding.setWecomUserId(item.getWecomUserId().trim());
                binding.setWecomUserName(item.getWecomUserId().trim());
                binding.setCreatedAt(now);
                binding.setDeleted(false);
            }
            binding.setCrmUserId(item.getCrmUserId());
            binding.setEnabled(true);
            binding.setUpdatedAt(now);
            if (bindingMapper.selectById(binding.getId()) == null) {
                bindingMapper.insert(binding);
            } else {
                bindingMapper.updateById(binding);
            }
        }
        return listBindings(tenantId, request.getConfigId());
    }

    @Transactional(readOnly = true)
    public WecomSyncTaskResponse latestTask(Long tenantId, Long configId) {
        findConfig(tenantId, configId);
        QueryWrapper<WecomSyncTaskEntity> wrapper = new QueryWrapper<WecomSyncTaskEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("deleted", false);
        wrapper.orderByDesc("created_at").last("limit 1");
        WecomSyncTaskEntity task = taskMapper.selectOne(wrapper);
        return task == null ? null : toTaskResponse(task);
    }

    @Transactional(readOnly = true)
    public WecomSyncTaskResponse taskDetail(Long tenantId, Long taskId) {
        QueryWrapper<WecomSyncTaskEntity> wrapper = new QueryWrapper<WecomSyncTaskEntity>();
        wrapper.eq("id", taskId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        WecomSyncTaskEntity task = taskMapper.selectOne(wrapper);
        if (task == null) {
            throw new BusinessException("WECOM_TASK_001", "企业微信同步任务不存在");
        }
        return toTaskResponse(task);
    }

    public WecomCorpConfigEntity findConfig(Long tenantId, Long configId) {
        if (configId == null) {
            throw new BusinessException("WECOM_CONFIG_003", "企业微信配置编号不能为空");
        }
        QueryWrapper<WecomCorpConfigEntity> wrapper = new QueryWrapper<WecomCorpConfigEntity>();
        wrapper.eq("id", configId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        WecomCorpConfigEntity config = configMapper.selectOne(wrapper);
        if (config == null) {
            throw new BusinessException("WECOM_CONFIG_004", "企业微信配置不存在");
        }
        return config;
    }

    public WecomCorpConfigEntity findFirstConfig(Long tenantId) {
        QueryWrapper<WecomCorpConfigEntity> wrapper = new QueryWrapper<WecomCorpConfigEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.orderByAsc("created_at").last("limit 1");
        return configMapper.selectOne(wrapper);
    }

    private WecomUserBindingEntity findBinding(Long tenantId, Long configId, String wecomUserId) {
        QueryWrapper<WecomUserBindingEntity> wrapper = new QueryWrapper<WecomUserBindingEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("wecom_user_id", wecomUserId);
        wrapper.eq("deleted", false);
        return bindingMapper.selectOne(wrapper);
    }

    private void validateConfig(WecomConfigSaveRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getName())
                || !StringUtils.hasText(request.getCorpId())) {
            throw new BusinessException("WECOM_CONFIG_001", "企业名称和企业ID不能为空");
        }
    }

    private int normalizeInterval(Integer value) {
        if (value == null) {
            return 10;
        }
        return Math.max(2, Math.min(1440, value.intValue()));
    }

    private WecomConfigResponse toConfigResponse(WecomCorpConfigEntity config) {
        WecomConfigResponse response = new WecomConfigResponse();
        response.setId(config.getId());
        response.setName(config.getName());
        response.setCorpId(config.getCorpId());
        response.setSecretConfigured(StringUtils.hasText(config.getCorpSecret()));
        response.setEnabled(config.isEnabled());
        response.setSyncIntervalMinutes(config.getSyncIntervalMinutes());
        response.setDefaultOwnerId(config.getDefaultOwnerId());
        response.setLastSyncStatus(config.getLastSyncStatus());
        response.setLastSyncAt(config.getLastSyncAt());
        response.setLastSuccessAt(config.getLastSuccessAt());
        response.setLastError(config.getLastError());
        return response;
    }

    private WecomBindingResponse toBindingResponse(WecomUserBindingEntity entity) {
        WecomBindingResponse response = new WecomBindingResponse();
        response.setId(entity.getId());
        response.setWecomUserId(entity.getWecomUserId());
        response.setWecomUserName(entity.getWecomUserName());
        response.setCrmUserId(entity.getCrmUserId());
        response.setEnabled(entity.isEnabled());
        return response;
    }

    public WecomSyncTaskResponse toTaskResponse(WecomSyncTaskEntity task) {
        WecomSyncTaskResponse response = new WecomSyncTaskResponse();
        response.setId(task.getId());
        response.setConfigId(task.getConfigId());
        response.setTriggerType(task.getTriggerType());
        response.setStatus(task.getStatus());
        response.setContactsFetched(task.getContactsFetched());
        response.setContactsCreated(task.getContactsCreated());
        response.setContactsUpdated(task.getContactsUpdated());
        response.setGroupsFetched(task.getGroupsFetched());
        response.setGroupMembersFetched(task.getGroupMembersFetched());
        response.setChannelsCreated(task.getChannelsCreated());
        response.setChannelsUpdated(task.getChannelsUpdated());
        response.setDuplicatesSkipped(task.getDuplicatesSkipped());
        response.setStartedAt(task.getStartedAt());
        response.setFinishedAt(task.getFinishedAt());
        response.setErrorMessage(task.getErrorMessage());
        return response;
    }
}
