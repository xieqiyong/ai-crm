package com.hz.crm.application.customer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.application.customer.dto.CustomerAssignRequest;
import com.hz.crm.application.customer.dto.CustomerQuery;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.application.customer.dto.CustomerSaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.AssignableUserResolver;
import com.hz.crm.common.user.UserDataScopeValidator;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.CustomerLevel;
import com.hz.crm.domain.customer.CustomerStatus;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.customer.repository.CustomerJpaRepository;
import java.time.LocalDateTime;
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
public class CustomerApplicationService {

    @Autowired
    private CustomerJpaRepository customerRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired(required = false)
    private UserNameResolver userNameResolver;

    @Autowired
    private AssignableUserResolver assignableUserResolver;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private UserDataScopeValidator userDataScopeValidator;

    @Transactional(readOnly = true)
    public PageData<CustomerResponse> page(Long tenantId, Long userId, String dataScope, CustomerQuery query) {
        CustomerQuery safeQuery = query == null ? new CustomerQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        String keyword = likeKeyword(safeQuery.getKeyword());
        Page<CustomerEntity> page;
        if ("SELF".equals(dataScope)) {
            page = customerRepository.searchByTenantIdAndOwnerId(tenantId, userId, keyword, safeQuery.getStatus(), pageRequest);
        } else {
            page = customerRepository.searchByTenantId(tenantId, keyword, safeQuery.getStatus(), pageRequest);
        }
        List<CustomerResponse> records = new ArrayList<CustomerResponse>();
        for (CustomerEntity entity : page.getContent()) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        return PageData.of(page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), records);
    }

    @Transactional(readOnly = true)
    public CustomerResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        CustomerEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        CustomerResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public CustomerResponse save(Long tenantId, Long operatorId, String dataScope, CustomerSaveRequest request) {
        if (request == null || trimToNull(request.getName()) == null) {
            throw new BusinessException("CUSTOMER_003", "客户名称不能为空");
        }
        CustomerEntity entity;
        if (request.getId() == null) {
            entity = new CustomerEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findOne(tenantId, request.getId());
            checkDataScope(operatorId, dataScope, entity.getOwnerId());
        }
        entity.setName(trimToNull(request.getName()));
        entity.setIndustry(trimToNull(request.getIndustry()));
        entity.setContactName(trimToNull(request.getContactName()));
        entity.setContactPhone(trimToNull(request.getContactPhone()));
        entity.setContactEmail(trimToNull(request.getContactEmail()));
        entity.setLevel(request.getLevel() == null ? CustomerLevel.NORMAL : request.getLevel());
        entity.setStatus(request.getStatus() == null ? CustomerStatus.recommended() : request.getStatus());
        Long targetOwnerId = request.getOwnerId() == null ? operatorId : request.getOwnerId();
        checkOwnerScope(operatorId, dataScope, targetOwnerId);
        entity.setOwnerId(targetOwnerId);
        entity.setRemark(trimToNull(request.getRemark()));
        CustomerResponse response = toResponse(customerRepository.save(entity));
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public CustomerResponse assign(
            Long tenantId, Long operatorId, String dataScope, CustomerAssignRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException("CUSTOMER_ASSIGN_001", "客户编号不能为空");
        }
        if (request.getOwnerId() == null) {
            throw new BusinessException("CUSTOMER_ASSIGN_002", "负责人不能为空");
        }
        CustomerEntity entity = findOneForAssignment(tenantId, request.getId());
        userDataScopeValidator.checkOwnerAccess(tenantId, operatorId, dataScope, entity.getOwnerId());
        checkOwnerScope(operatorId, dataScope, request.getOwnerId());
        String ownerName = assignableUserResolver.resolveAssignableName(
                tenantId, operatorId, dataScope, request.getOwnerId());
        if (!request.getOwnerId().equals(entity.getOwnerId())) {
            LocalDateTime updatedAt = DateTimes.now();
            int updated = customerMapper.update(null, Wrappers.<CustomerEntity>lambdaUpdate()
                    .eq(CustomerEntity::getId, entity.getId())
                    .eq(CustomerEntity::getTenantId, tenantId)
                    .eq(CustomerEntity::isDeleted, false)
                    .set(CustomerEntity::getOwnerId, request.getOwnerId())
                    .set(CustomerEntity::getUpdatedAt, updatedAt));
            if (updated != 1) {
                throw new BusinessException("CUSTOMER_ASSIGN_004", "客户分配失败，请刷新后重试");
            }
            entity.setOwnerId(request.getOwnerId());
            entity.setUpdatedAt(updatedAt);
        }
        CustomerResponse response = toResponse(entity);
        response.setOwnerName(ownerName);
        return response;
    }

    @Transactional
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        CustomerEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        customerRepository.save(entity);
    }

    private CustomerEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("CUSTOMER_001", "客户编号不能为空");
        }
        return customerRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("CUSTOMER_002", "客户不存在"));
    }

    private CustomerEntity findOneForAssignment(Long tenantId, Long id) {
        CustomerEntity entity = customerMapper.selectOne(Wrappers.<CustomerEntity>lambdaQuery()
                .eq(CustomerEntity::getId, id)
                .eq(CustomerEntity::getTenantId, tenantId)
                .eq(CustomerEntity::isDeleted, false));
        if (entity == null) {
            throw new BusinessException("CUSTOMER_ASSIGN_003", "客户不存在");
        }
        return entity;
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

    private CustomerResponse toResponse(CustomerEntity entity) {
        CustomerResponse response = new CustomerResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setName(entity.getName());
        response.setIndustry(entity.getIndustry());
        response.setContactName(entity.getContactName());
        response.setContactPhone(entity.getContactPhone());
        response.setContactEmail(entity.getContactEmail());
        response.setLevel(entity.getLevel());
        response.setStatus(entity.getStatus());
        response.setOwnerId(entity.getOwnerId());
        response.setRemark(entity.getRemark());
        response.setAiSummary(entity.getAiSummary());
        response.setAiAnalyzedAt(entity.getAiAnalyzedAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private void fillOwnerName(Long tenantId, CustomerResponse response) {
        List<CustomerResponse> records = new ArrayList<CustomerResponse>();
        records.add(response);
        fillOwnerNames(tenantId, records);
    }

    private void fillOwnerNames(Long tenantId, List<CustomerResponse> records) {
        if (userNameResolver == null || records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ownerIds = new HashSet<Long>();
        for (CustomerResponse response : records) {
            if (response.getOwnerId() != null) {
                ownerIds.add(response.getOwnerId());
            }
        }
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userNameResolver.resolve(tenantId, ownerIds);
        for (CustomerResponse response : records) {
            if (response.getOwnerId() != null) {
                response.setOwnerName(names.get(response.getOwnerId()));
            }
        }
    }
}
