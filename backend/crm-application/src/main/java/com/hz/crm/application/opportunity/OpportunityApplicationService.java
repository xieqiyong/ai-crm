package com.hz.crm.application.opportunity;

import com.hz.crm.application.opportunity.dto.OpportunityQuery;
import com.hz.crm.application.opportunity.dto.OpportunityResponse;
import com.hz.crm.application.opportunity.dto.OpportunitySaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.OpportunityStage;
import com.hz.crm.domain.opportunity.repository.OpportunityJpaRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    @Autowired(required = false)
    private UserNameResolver userNameResolver;

    @Transactional(readOnly = true)
    public PageData<OpportunityResponse> page(Long tenantId, Long userId, String dataScope, OpportunityQuery query) {
        OpportunityQuery safeQuery = query == null ? new OpportunityQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Long ownerId = "SELF".equals(dataScope) ? userId : null;
        Page<OpportunityEntity> page = opportunityRepository.search(
                tenantId, ownerId, likeKeyword(safeQuery.getKeyword()), safeQuery.getStage(), pageRequest);
        List<OpportunityResponse> records = new ArrayList<OpportunityResponse>();
        for (OpportunityEntity entity : page.getContent()) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        return PageData.of(page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), records);
    }

    @Transactional(readOnly = true)
    public OpportunityResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        OpportunityEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        OpportunityResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public OpportunityResponse save(Long tenantId, Long operatorId, String dataScope, OpportunitySaveRequest request) {
        if (request == null || trimToNull(request.getName()) == null) {
            throw new BusinessException("OPPORTUNITY_003", "商机名称不能为空");
        }
        OpportunityEntity entity;
        if (request.getId() == null) {
            entity = new OpportunityEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findOne(tenantId, request.getId());
            checkDataScope(operatorId, dataScope, entity.getOwnerId());
        }
        entity.setName(trimToNull(request.getName()));
        entity.setCustomerId(request.getCustomerId());
        entity.setAmount(request.getAmount());
        entity.setStage(request.getStage() == null ? OpportunityStage.DISCOVERY : request.getStage());
        entity.setProbability(request.getProbability());
        entity.setExpectedCloseDate(request.getExpectedCloseDate());
        Long targetOwnerId = request.getOwnerId() == null ? operatorId : request.getOwnerId();
        checkOwnerScope(operatorId, dataScope, targetOwnerId);
        entity.setOwnerId(targetOwnerId);
        entity.setRemark(trimToNull(request.getRemark()));
        OpportunityResponse response = toResponse(opportunityRepository.save(entity));
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        OpportunityEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        opportunityRepository.save(entity);
    }

    private OpportunityEntity findOne(Long tenantId, Long id) {
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

    private void checkOwnerScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_002", "本人数据权限不能分配给其他负责人");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }

    private String likeKeyword(String value) {
        String keyword = trimToNull(value);
        return keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
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

    private void fillOwnerName(Long tenantId, OpportunityResponse response) {
        List<OpportunityResponse> records = new ArrayList<OpportunityResponse>();
        records.add(response);
        fillOwnerNames(tenantId, records);
    }

    private void fillOwnerNames(Long tenantId, List<OpportunityResponse> records) {
        if (userNameResolver == null || records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ownerIds = new HashSet<Long>();
        for (OpportunityResponse response : records) {
            if (response.getOwnerId() != null) {
                ownerIds.add(response.getOwnerId());
            }
        }
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userNameResolver.resolve(tenantId, ownerIds);
        for (OpportunityResponse response : records) {
            if (response.getOwnerId() != null) {
                response.setOwnerName(names.get(response.getOwnerId()));
            }
        }
    }
}
