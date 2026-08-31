package com.hz.crm.application.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.application.customer.dto.CustomerAssignRequest;
import com.hz.crm.application.customer.dto.CustomerIndustryOptionResponse;
import com.hz.crm.application.customer.dto.CustomerQuery;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.application.customer.dto.CustomerSaveRequest;
import com.hz.crm.application.product.ProductReferenceResolver;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.AssignableUserResolver;
import com.hz.crm.common.user.UserDataScopeValidator;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.CustomerIndustry;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerApplicationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$");

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

    @Autowired
    private ProductReferenceResolver productReferenceResolver;

    @Transactional(readOnly = true)
    public PageData<CustomerResponse> page(Long tenantId, Long userId, String dataScope, CustomerQuery query) {
        CustomerQuery safeQuery = query == null ? new CustomerQuery() : query;
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        long offset = (long) (pageNo - 1) * pageSize;
        LambdaQueryWrapper<CustomerEntity> wrapper = buildPageWrapper(
                tenantId, userId, dataScope, safeQuery);
        Long total = customerMapper.selectCount(wrapper);
        wrapper.orderByDesc(CustomerEntity::getCreatedAt)
                .orderByDesc(CustomerEntity::getId)
                .last("LIMIT " + pageSize + " OFFSET " + offset);
        List<CustomerEntity> entities = customerMapper.selectList(wrapper);
        List<CustomerResponse> records = new ArrayList<CustomerResponse>();
        for (CustomerEntity entity : entities) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        fillProductNames(tenantId, records);
        return PageData.of(total == null ? 0L : total.longValue(), pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public List<CustomerIndustryOptionResponse> industryOptions() {
        List<CustomerIndustryOptionResponse> options = new ArrayList<CustomerIndustryOptionResponse>();
        for (CustomerIndustry industry : CustomerIndustry.values()) {
            options.add(new CustomerIndustryOptionResponse(industry.getValue(), industry.getValue()));
        }
        return options;
    }

    @Transactional(readOnly = true)
    public CustomerResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        CustomerEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        CustomerResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillProductName(tenantId, response);
        return response;
    }

    @Transactional
    public CustomerResponse save(Long tenantId, Long operatorId, String dataScope, CustomerSaveRequest request) {
        validateCustomerRequest(request);
        CustomerEntity entity;
        boolean creating = request.getId() == null;
        if (request.getId() == null) {
            entity = new CustomerEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setDeleted(false);
            entity.setCreatedAt(DateTimes.now());
        } else {
            entity = findOne(tenantId, request.getId());
            checkDataScope(operatorId, dataScope, entity.getOwnerId());
        }
        productReferenceResolver.requireSelectable(tenantId, request.getProductId(), entity.getProductId());
        entity.setName(trimToNull(request.getName()));
        entity.setIndustry(request.getIndustry().trim());
        entity.setContactName(request.getContactName().trim());
        entity.setContactPhone(request.getContactPhone().trim());
        entity.setContactEmail(request.getContactEmail().trim());
        entity.setLevel(request.getLevel());
        entity.setStatus(request.getStatus());
        Long targetOwnerId = request.getOwnerId();
        checkOwnerScope(operatorId, dataScope, targetOwnerId);
        entity.setOwnerId(targetOwnerId);
        entity.setProductId(request.getProductId());
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setUpdatedAt(DateTimes.now());
        int affected = creating ? customerMapper.insert(entity) : customerMapper.updateById(entity);
        if (affected != 1) {
            throw new BusinessException("CUSTOMER_013", "客户保存失败，请刷新后重试");
        }
        CustomerResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillProductName(tenantId, response);
        return response;
    }

    @Transactional
    public CustomerResponse changeIntendedProduct(
            Long tenantId,
            Long operatorId,
            String dataScope,
            Long customerId,
            Long productId) {
        CustomerEntity entity = findOne(tenantId, customerId);
        checkDataScope(operatorId, dataScope, entity.getOwnerId());
        productReferenceResolver.require(tenantId, productId);
        if (!productId.equals(entity.getProductId())) {
            LocalDateTime updatedAt = DateTimes.now();
            int updated = customerMapper.update(null, Wrappers.<CustomerEntity>lambdaUpdate()
                    .eq(CustomerEntity::getId, customerId)
                    .eq(CustomerEntity::getTenantId, tenantId)
                    .eq(CustomerEntity::isDeleted, false)
                    .set(CustomerEntity::getProductId, productId)
                    .set(CustomerEntity::getUpdatedAt, updatedAt));
            if (updated != 1) {
                throw new BusinessException("CUSTOMER_015", "客户意向产品更新失败，请刷新后重试");
            }
            entity.setProductId(productId);
            entity.setUpdatedAt(updatedAt);
        }
        CustomerResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillProductName(tenantId, response);
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
        fillProductName(tenantId, response);
        return response;
    }

    @Transactional
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        CustomerEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        int affected = customerMapper.update(null, Wrappers.<CustomerEntity>lambdaUpdate()
                .eq(CustomerEntity::getId, id)
                .eq(CustomerEntity::getTenantId, tenantId)
                .eq(CustomerEntity::isDeleted, false)
                .set(CustomerEntity::isDeleted, true)
                .set(CustomerEntity::getUpdatedAt, DateTimes.now()));
        if (affected != 1) {
            throw new BusinessException("CUSTOMER_014", "客户删除失败，请刷新后重试");
        }
    }

    private CustomerEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("CUSTOMER_001", "客户编号不能为空");
        }
        CustomerEntity entity = customerMapper.selectOne(Wrappers.<CustomerEntity>lambdaQuery()
                .eq(CustomerEntity::getId, id)
                .eq(CustomerEntity::getTenantId, tenantId)
                .eq(CustomerEntity::isDeleted, false));
        if (entity == null) {
            throw new BusinessException("CUSTOMER_002", "客户不存在");
        }
        return entity;
    }

    private CustomerEntity findOneForAssignment(Long tenantId, Long id) {
        return findOne(tenantId, id);
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

    private void validateCustomerRequest(CustomerSaveRequest request) {
        if (request == null || trimToNull(request.getName()) == null) {
            throw new BusinessException("CUSTOMER_003", "客户名称不能为空");
        }
        String industry = trimToNull(request.getIndustry());
        if (industry == null) {
            throw new BusinessException("CUSTOMER_004", "行业不能为空");
        }
        if (!CustomerIndustry.supports(industry)) {
            throw new BusinessException("CUSTOMER_005", "请选择系统支持的行业");
        }
        if (trimToNull(request.getContactName()) == null) {
            throw new BusinessException("CUSTOMER_006", "主要联系人不能为空");
        }
        if (trimToNull(request.getContactPhone()) == null) {
            throw new BusinessException("CUSTOMER_007", "联系电话不能为空");
        }
        String contactEmail = trimToNull(request.getContactEmail());
        if (contactEmail == null) {
            throw new BusinessException("CUSTOMER_008", "联系邮箱不能为空");
        }
        if (!EMAIL_PATTERN.matcher(contactEmail).matches()) {
            throw new BusinessException("CUSTOMER_009", "联系邮箱格式不正确");
        }
        if (request.getLevel() == null) {
            throw new BusinessException("CUSTOMER_010", "客户级别不能为空");
        }
        if (request.getStatus() == null) {
            throw new BusinessException("CUSTOMER_011", "客户状态不能为空");
        }
        if (request.getOwnerId() == null) {
            throw new BusinessException("CUSTOMER_012", "负责人不能为空");
        }
        if (request.getProductId() == null) {
            throw new BusinessException("CUSTOMER_016", "意向产品不能为空");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }

    private LambdaQueryWrapper<CustomerEntity> buildPageWrapper(
            Long tenantId, Long userId, String dataScope, CustomerQuery query) {
        LambdaQueryWrapper<CustomerEntity> wrapper = Wrappers.<CustomerEntity>lambdaQuery()
                .eq(CustomerEntity::getTenantId, tenantId)
                .eq(CustomerEntity::isDeleted, false);
        if ("SELF".equals(dataScope)) {
            wrapper.eq(CustomerEntity::getOwnerId, userId);
        }
        if (query.getStatus() != null) {
            wrapper.eq(CustomerEntity::getStatus, query.getStatus());
        }
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested
                    .like(CustomerEntity::getName, keyword)
                    .or()
                    .like(CustomerEntity::getIndustry, keyword)
                    .or()
                    .like(CustomerEntity::getContactName, keyword)
                    .or()
                    .like(CustomerEntity::getContactPhone, keyword)
                    .or()
                    .like(CustomerEntity::getContactEmail, keyword));
        }
        return wrapper;
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
        response.setProductId(entity.getProductId());
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

    private void fillProductName(Long tenantId, CustomerResponse response) {
        List<CustomerResponse> records = new ArrayList<CustomerResponse>();
        records.add(response);
        fillProductNames(tenantId, records);
    }

    private void fillProductNames(Long tenantId, List<CustomerResponse> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> productIds = new HashSet<Long>();
        for (CustomerResponse response : records) {
            if (response.getProductId() != null) {
                productIds.add(response.getProductId());
            }
        }
        Map<Long, String> names = productReferenceResolver.resolveNames(tenantId, productIds);
        for (CustomerResponse response : records) {
            if (response.getProductId() != null) {
                response.setProductName(names.get(response.getProductId()));
            }
        }
    }
}
