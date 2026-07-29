package com.hz.crm.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.observability.domain.AuditLogEntity;
import com.hz.crm.observability.dto.AuditLogQuery;
import com.hz.crm.observability.dto.AuditLogRecord;
import com.hz.crm.observability.mapper.AuditLogMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    public PageData<AuditLogEntity> page(Long tenantId, AuditLogQuery query) {
        AuditLogQuery safeQuery = query == null ? new AuditLogQuery() : query;
        LambdaQueryWrapper<AuditLogEntity> wrapper = Wrappers.<AuditLogEntity>lambdaQuery()
                .eq(AuditLogEntity::getTenantId, tenantId);
        String module = trimToNull(safeQuery.getModule());
        String action = trimToNull(safeQuery.getAction());
        if (module != null) {
            wrapper.likeRight(AuditLogEntity::getAction, module + ":");
        }
        if (action != null) {
            wrapper.like(AuditLogEntity::getAction, ":" + action);
        }
        if (trimToNull(safeQuery.getTargetType()) != null) {
            wrapper.eq(AuditLogEntity::getTargetType, safeQuery.getTargetType().trim());
        }
        if (safeQuery.getOperatorId() != null) {
            wrapper.eq(AuditLogEntity::getOperatorId, safeQuery.getOperatorId());
        }
        Long total = auditLogMapper.selectCount(wrapper);
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        long offset = (long) (pageNo - 1) * pageSize;
        wrapper.orderByDesc(AuditLogEntity::getCreatedAt)
                .last("limit " + pageSize + " offset " + offset);
        List<AuditLogEntity> records = auditLogMapper.selectList(wrapper);
        return PageData.of(total == null ? 0L : total.longValue(), pageNo, pageSize, records);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditLogRecord record) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(record.getTenantId());
        entity.setOperatorId(record.getOperatorId());
        entity.setAction(record.getAction());
        entity.setTargetType(record.getTargetType());
        entity.setTargetId(record.getTargetId());
        entity.setDetailJson(record.getDetailJson());
        entity.setCreatedAt(DateTimes.now());
        auditLogMapper.insert(entity);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }
}
