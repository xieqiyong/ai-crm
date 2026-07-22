package com.hz.crm.application.customer;

import com.hz.crm.application.customer.dto.CustomerQuery;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.application.customer.dto.CustomerSaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.CustomerLevel;
import com.hz.crm.domain.customer.repository.CustomerJpaRepository;
import java.util.ArrayList;
import java.util.List;
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

    @Transactional(readOnly = true)
    public PageData<CustomerResponse> page(String tenantId, Long userId, String dataScope, CustomerQuery query) {
        CustomerQuery safeQuery = query == null ? new CustomerQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        String keyword = trimToNull(safeQuery.getKeyword());
        Page<CustomerEntity> page;
        if ("SELF".equals(dataScope)) {
            page = customerRepository.searchByTenantIdAndOwnerId(tenantId, userId, keyword, pageRequest);
        } else {
            page = customerRepository.searchByTenantId(tenantId, keyword, pageRequest);
        }
        List<CustomerResponse> records = new ArrayList<CustomerResponse>();
        for (CustomerEntity entity : page.getContent()) {
            records.add(toResponse(entity));
        }
        return PageData.of(page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), records);
    }

    @Transactional(readOnly = true)
    public CustomerResponse detail(String tenantId, Long userId, String dataScope, Long id) {
        CustomerEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        return toResponse(entity);
    }

    @Transactional
    public CustomerResponse save(String tenantId, Long operatorId, CustomerSaveRequest request) {
        CustomerEntity entity;
        if (request.getId() == null) {
            entity = new CustomerEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findOne(tenantId, request.getId());
        }
        entity.setName(request.getName());
        entity.setIndustry(request.getIndustry());
        entity.setContactName(request.getContactName());
        entity.setContactPhone(request.getContactPhone());
        entity.setContactEmail(request.getContactEmail());
        entity.setLevel(request.getLevel() == null ? CustomerLevel.NORMAL : request.getLevel());
        entity.setOwnerId(request.getOwnerId() == null ? operatorId : request.getOwnerId());
        entity.setRemark(request.getRemark());
        return toResponse(customerRepository.save(entity));
    }

    @Transactional
    public void delete(String tenantId, Long id) {
        CustomerEntity entity = findOne(tenantId, id);
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

    private String trimToNull(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
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
        response.setOwnerId(entity.getOwnerId());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
