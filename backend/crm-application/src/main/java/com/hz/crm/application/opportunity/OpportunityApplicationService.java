package com.hz.crm.application.opportunity;

import com.hz.crm.application.opportunity.dto.OpportunityQuery;
import com.hz.crm.application.opportunity.dto.OpportunityResponse;
import com.hz.crm.application.opportunity.dto.OpportunitySaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.OpportunityStage;
import com.hz.crm.domain.opportunity.repository.OpportunityJpaRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpportunityApplicationService {

    @Autowired
    private OpportunityJpaRepository opportunityRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(readOnly = true)
    public PageData<OpportunityResponse> page(String tenantId, Long userId, String dataScope, OpportunityQuery query) {
        OpportunityQuery safeQuery = query == null ? new OpportunityQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OpportunityEntity> page;
        if ("SELF".equals(dataScope)) {
            page = opportunityRepository.findByTenantIdAndOwnerIdAndDeletedFalse(tenantId, userId, pageRequest);
        } else {
            page = opportunityRepository.findByTenantIdAndDeletedFalse(tenantId, pageRequest);
        }
        List<OpportunityResponse> records = new ArrayList<OpportunityResponse>();
        for (OpportunityEntity entity : page.getContent()) {
            records.add(toResponse(entity));
        }
        return PageData.of(page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), records);
    }

    @Transactional(readOnly = true)
    public OpportunityResponse detail(String tenantId, Long userId, String dataScope, Long id) {
        OpportunityEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        return toResponse(entity);
    }

    @Transactional
    public OpportunityResponse save(String tenantId, Long operatorId, OpportunitySaveRequest request) {
        OpportunityEntity entity;
        if (request.getId() == null) {
            entity = new OpportunityEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findOne(tenantId, request.getId());
        }
        entity.setName(request.getName());
        entity.setCustomerId(request.getCustomerId());
        entity.setAmount(request.getAmount());
        entity.setStage(request.getStage() == null ? OpportunityStage.DISCOVERY : request.getStage());
        entity.setProbability(request.getProbability());
        entity.setExpectedCloseDate(request.getExpectedCloseDate());
        entity.setOwnerId(request.getOwnerId() == null ? operatorId : request.getOwnerId());
        entity.setRemark(request.getRemark());
        return toResponse(opportunityRepository.save(entity));
    }

    @Transactional
    public void delete(String tenantId, Long id) {
        OpportunityEntity entity = findOne(tenantId, id);
        entity.setDeleted(true);
        opportunityRepository.save(entity);
    }

    private OpportunityEntity findOne(String tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("OPPORTUNITY_001", "商机编号不能为空");
        }
        return opportunityRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("OPPORTUNITY_002", "商机不存在"));
    }

    private void checkDataScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_001", "无权访问该数据");
        }
    }

    private OpportunityResponse toResponse(OpportunityEntity entity) {
        OpportunityResponse response = new OpportunityResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setName(entity.getName());
        response.setCustomerId(entity.getCustomerId());
        response.setAmount(entity.getAmount());
        response.setStage(entity.getStage());
        response.setProbability(entity.getProbability());
        response.setExpectedCloseDate(entity.getExpectedCloseDate());
        response.setOwnerId(entity.getOwnerId());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
