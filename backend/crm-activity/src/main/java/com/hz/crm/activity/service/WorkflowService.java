package com.hz.crm.activity.service;

import com.hz.crm.activity.domain.WorkflowDefinitionEntity;
import com.hz.crm.activity.domain.WorkflowInstanceEntity;
import com.hz.crm.activity.repository.WorkflowDefinitionRepository;
import com.hz.crm.activity.repository.WorkflowInstanceRepository;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {

    @Autowired
    private WorkflowDefinitionRepository definitionRepository;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public Long start(String tenantId, String definitionCode, String businessType, Long businessId) {
        WorkflowDefinitionEntity definition = definitionRepository
                .findByCodeAndTenantIdAndEnabledTrueAndDeletedFalse(definitionCode, tenantId)
                .orElseThrow(() -> new BusinessException("WF_001", "流程定义不存在"));
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(snowflakeIdGenerator.nextId());
        instance.setTenantId(tenantId);
        instance.setDefinitionId(definition.getId());
        instance.setBusinessType(businessType);
        instance.setBusinessId(businessId);
        instance.setStatus("RUNNING");
        instanceRepository.save(instance);
        return instance.getId();
    }
}
