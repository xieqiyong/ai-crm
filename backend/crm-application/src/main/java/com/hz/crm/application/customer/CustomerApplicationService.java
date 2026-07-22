package com.hz.crm.application.customer;

import com.hz.crm.application.customer.dto.CustomerQuery;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.application.customer.dto.CustomerSaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.CustomerLevel;
import com.hz.crm.domain.customer.CustomerStatus;
import com.hz.crm.domain.customer.repository.CustomerJpaRepository;
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

    @Transactional(readOnly = true)
    public PageData<CustomerResponse> page(String tenantId, Long userId, String dataScope, CustomerQuery query) {
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
    public CustomerResponse detail(String tenantId, Long userId, String dataScope, Long id) {
        CustomerEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        CustomerResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public CustomerResponse save(String tenantId, Long operatorId, String dataScope, CustomerSaveRequest request) {
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
    public void delete(String tenantId, Long userId, String dataScope, Long id) {
        CustomerEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        customerRepository.save(entity);
    }

    private CustomerEntity findOne(String tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("CUSTOMER_001", "客户编号不能为空");
        }
        return customerRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("CUSTOMER_002", "客户不存在"));
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
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private void fillOwnerName(String tenantId, CustomerResponse response) {
        List<CustomerResponse> records = new ArrayList<CustomerResponse>();
        records.add(response);
        fillOwnerNames(tenantId, records);
    }

    private void fillOwnerNames(String tenantId, List<CustomerResponse> records) {
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
