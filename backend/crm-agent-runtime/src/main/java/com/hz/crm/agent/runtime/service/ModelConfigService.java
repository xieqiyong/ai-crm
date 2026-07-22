package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.domain.ModelConfigEntity;
import com.hz.crm.agent.runtime.dto.ModelConfigIdRequest;
import com.hz.crm.agent.runtime.dto.ModelConfigRequest;
import com.hz.crm.agent.runtime.dto.ModelConfigResponse;
import com.hz.crm.agent.runtime.dto.ModelConfigStatusResponse;
import com.hz.crm.agent.runtime.repository.ModelConfigRepository;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelConfigService {

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(readOnly = true)
    public List<ModelConfigResponse> list(String tenantId) {
        List<ModelConfigEntity> entities = modelConfigRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId);
        List<ModelConfigResponse> responses = new ArrayList<ModelConfigResponse>();
        for (ModelConfigEntity entity : entities) {
            responses.add(toResponse(entity));
        }
        return responses;
    }

    @Transactional
    public ModelConfigResponse save(String tenantId, ModelConfigRequest request) {
        if (request == null || blank(request.getName()) || blank(request.getModelName()) || blank(request.getApiKeyEnv())) {
            throw new BusinessException("MODEL_001", "模型名称、模型标识和密钥环境变量不能为空");
        }
        ModelConfigEntity entity;
        if (request.getId() == null) {
            entity = new ModelConfigEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findConfig(tenantId, request.getId());
        }
        entity.setProvider(blank(request.getProvider()) ? "OPENAI" : request.getProvider().trim());
        entity.setName(request.getName().trim());
        entity.setModelName(request.getModelName().trim());
        entity.setBaseUrl(trimToNull(request.getBaseUrl()));
        entity.setApiKeyEnv(request.getApiKeyEnv().trim());
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setDefaultConfig(request.getDefaultConfig() != null && request.getDefaultConfig());
        if (entity.isDefaultConfig()) {
            clearDefault(tenantId, entity.getId());
        }
        return toResponse(modelConfigRepository.save(entity));
    }

    @Transactional
    public void delete(String tenantId, ModelConfigIdRequest request) {
        ModelConfigEntity entity = findConfig(tenantId, request == null ? null : request.getId());
        entity.setDeleted(true);
        entity.setDefaultConfig(false);
        modelConfigRepository.save(entity);
    }

    @Transactional
    public ModelConfigResponse setDefault(String tenantId, ModelConfigIdRequest request) {
        ModelConfigEntity entity = findConfig(tenantId, request == null ? null : request.getId());
        clearDefault(tenantId, entity.getId());
        entity.setDefaultConfig(true);
        entity.setEnabled(true);
        return toResponse(modelConfigRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ModelConfigStatusResponse status(String tenantId, ModelConfigIdRequest request) {
        ModelConfigEntity entity = findConfig(tenantId, request == null ? null : request.getId());
        ModelConfigStatusResponse response = new ModelConfigStatusResponse();
        response.setId(entity.getId());
        boolean available = System.getenv(entity.getApiKeyEnv()) != null
                && System.getenv(entity.getApiKeyEnv()).trim().length() > 0;
        response.setAvailable(available);
        response.setMessage(available ? "密钥环境变量已配置" : "密钥环境变量未配置");
        return response;
    }

    private void clearDefault(String tenantId, Long keepId) {
        List<ModelConfigEntity> defaults =
                modelConfigRepository.findByTenantIdAndDefaultConfigTrueAndDeletedFalse(tenantId);
        for (ModelConfigEntity item : defaults) {
            if (!item.getId().equals(keepId)) {
                item.setDefaultConfig(false);
                modelConfigRepository.save(item);
            }
        }
    }

    private ModelConfigEntity findConfig(String tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("MODEL_002", "模型配置编号不能为空");
        }
        return modelConfigRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("MODEL_003", "模型配置不存在"));
    }

    private ModelConfigResponse toResponse(ModelConfigEntity entity) {
        ModelConfigResponse response = new ModelConfigResponse();
        response.setId(entity.getId());
        response.setProvider(entity.getProvider());
        response.setName(entity.getName());
        response.setModelName(entity.getModelName());
        response.setBaseUrl(entity.getBaseUrl());
        response.setApiKeyEnv(entity.getApiKeyEnv());
        response.setRemark(entity.getRemark());
        response.setDefaultConfig(entity.isDefaultConfig());
        response.setEnabled(entity.isEnabled());
        response.setApiKeyConfigured(System.getenv(entity.getApiKeyEnv()) != null
                && System.getenv(entity.getApiKeyEnv()).trim().length() > 0);
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
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
