package com.hz.crm.application.lead;

import com.hz.crm.application.lead.dto.LeadQuery;
import com.hz.crm.application.lead.dto.LeadResponse;
import com.hz.crm.application.lead.dto.LeadSaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.LeadStatus;
import com.hz.crm.domain.lead.repository.LeadJpaRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadApplicationService {

    @Autowired
    private LeadJpaRepository leadRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(readOnly = true)
    public PageData<LeadResponse> page(String tenantId, Long userId, String dataScope, LeadQuery query) {
        LeadQuery safeQuery = query == null ? new LeadQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LeadEntity> page;
        if ("SELF".equals(dataScope)) {
            page = leadRepository.findByTenantIdAndOwnerIdAndDeletedFalse(tenantId, userId, pageRequest);
        } else {
            page = leadRepository.findByTenantIdAndDeletedFalse(tenantId, pageRequest);
        }
        List<LeadResponse> records = new ArrayList<LeadResponse>();
        for (LeadEntity entity : page.getContent()) {
            records.add(toResponse(entity));
        }
        return PageData.of(page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), records);
    }

    @Transactional(readOnly = true)
    public LeadResponse detail(String tenantId, Long userId, String dataScope, Long id) {
        LeadEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        return toResponse(entity);
    }

    @Transactional
    public LeadResponse save(String tenantId, Long operatorId, LeadSaveRequest request) {
        LeadEntity entity;
        if (request.getId() == null) {
            entity = new LeadEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findOne(tenantId, request.getId());
        }
        entity.setName(request.getName());
        entity.setCompanyName(request.getCompanyName());
        entity.setPhone(request.getPhone());
        entity.setEmail(request.getEmail());
        entity.setSource(request.getSource());
        entity.setStatus(request.getStatus() == null ? LeadStatus.NEW : request.getStatus());
        entity.setOwnerId(request.getOwnerId() == null ? operatorId : request.getOwnerId());
        entity.setRemark(request.getRemark());
        return toResponse(leadRepository.save(entity));
    }

    @Transactional
    public void delete(String tenantId, Long id) {
        LeadEntity entity = findOne(tenantId, id);
        entity.setDeleted(true);
        leadRepository.save(entity);
    }

    private LeadEntity findOne(String tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("LEAD_001", "线索编号不能为空");
        }
        return leadRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("LEAD_002", "线索不存在"));
    }

    private void checkDataScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_001", "无权访问该数据");
        }
    }

    private LeadResponse toResponse(LeadEntity entity) {
        LeadResponse response = new LeadResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setName(entity.getName());
        response.setCompanyName(entity.getCompanyName());
        response.setPhone(entity.getPhone());
        response.setEmail(entity.getEmail());
        response.setSource(entity.getSource());
        response.setStatus(entity.getStatus());
        response.setOwnerId(entity.getOwnerId());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
