package com.hz.crm.observability.service;

import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.observability.domain.AuditLogEntity;
import com.hz.crm.observability.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public void record(
            String tenantId, Long operatorId, String action, String targetType, Long targetId, String detailJson) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(tenantId);
        entity.setOperatorId(operatorId);
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetailJson(detailJson);
        auditLogRepository.save(entity);
    }
}
