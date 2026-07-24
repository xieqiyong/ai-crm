package com.hz.crm.observability.service;

import com.hz.crm.common.api.PageData;
import com.hz.crm.common.api.PageQuery;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.observability.domain.RequestLogEntity;
import com.hz.crm.observability.dto.RequestLogRecord;
import com.hz.crm.observability.repository.RequestLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestLogService {

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(readOnly = true)
    public PageData<RequestLogEntity> page(Long tenantId, PageQuery query) {
        PageQuery safeQuery = query == null ? new PageQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RequestLogEntity> page = requestLogRepository.findByTenantId(tenantId, pageRequest);
        return PageData.of(page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), page.getContent());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(RequestLogRecord record) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(record.getTenantId());
        entity.setOperatorId(record.getOperatorId());
        entity.setUsername(record.getUsername());
        entity.setTraceId(record.getTraceId());
        entity.setRequestMethod(record.getRequestMethod());
        entity.setRequestUri(record.getRequestUri());
        entity.setClientIp(record.getClientIp());
        entity.setUserAgent(record.getUserAgent());
        entity.setStatusCode(record.getStatusCode());
        entity.setCostMillis(record.getCostMillis());
        entity.setSuccess(record.isSuccess());
        entity.setErrorCode(record.getErrorCode());
        entity.setErrorMessage(record.getErrorMessage());
        requestLogRepository.save(entity);
    }
}
