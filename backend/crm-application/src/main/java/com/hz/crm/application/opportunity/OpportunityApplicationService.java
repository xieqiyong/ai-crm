package com.hz.crm.application.opportunity;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.opportunity.dto.OpportunityQuery;
import com.hz.crm.application.opportunity.dto.OpportunityProductResponse;
import com.hz.crm.application.opportunity.dto.OpportunityProductSaveRequest;
import com.hz.crm.application.opportunity.dto.OpportunityResponse;
import com.hz.crm.application.opportunity.dto.OpportunitySaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.OpportunityProductEntity;
import com.hz.crm.domain.opportunity.OpportunityStage;
import com.hz.crm.domain.opportunity.mapper.OpportunityMapper;
import com.hz.crm.domain.opportunity.mapper.OpportunityProductMapper;
import com.hz.crm.domain.product.ProductEntity;
import com.hz.crm.domain.product.mapper.ProductMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OpportunityApplicationService {

    @Autowired
    private OpportunityMapper opportunityMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OpportunityProductMapper opportunityProductMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired(required = false)
    private UserNameResolver userNameResolver;

    @Transactional(readOnly = true)
    public PageData<OpportunityResponse> page(Long tenantId, Long userId, String dataScope, OpportunityQuery query) {
        OpportunityQuery safeQuery = query == null ? new OpportunityQuery() : query;
        long total = opportunityMapper.selectCount(buildQueryWrapper(tenantId, userId, dataScope, safeQuery));
        QueryWrapper<OpportunityEntity> wrapper = buildQueryWrapper(tenantId, userId, dataScope, safeQuery);
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        int offset = (pageNo - 1) * pageSize;
        wrapper.orderByDesc("created_at").last("limit " + pageSize + " offset " + offset);
        List<OpportunityEntity> entities = opportunityMapper.selectList(wrapper);
        List<OpportunityResponse> records = new ArrayList<OpportunityResponse>();
        for (OpportunityEntity entity : entities) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        fillCustomerNames(tenantId, records);
        fillProductLines(tenantId, records);
        return PageData.of(total, pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public OpportunityResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        OpportunityEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        OpportunityResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillCustomerName(tenantId, response);
        fillProductLines(tenantId, response);
        return response;
    }

    @Transactional
    public OpportunityResponse save(Long tenantId, Long operatorId, String dataScope, OpportunitySaveRequest request) {
        if (request == null || trimToNull(request.getName()) == null) {
            throw new BusinessException("OPPORTUNITY_003", "商机名称不能为空");
        }
        if (request.getCustomerId() != null) {
            CustomerEntity customer = findCustomer(tenantId, request.getCustomerId());
            checkDataScope(operatorId, dataScope, customer.getOwnerId());
        }
        OpportunityEntity entity;
        LocalDateTime now = DateTimes.now();
        if (request.getId() == null) {
            entity = new OpportunityEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setCreatedAt(now);
        } else {
            entity = findOne(tenantId, request.getId());
            checkDataScope(operatorId, dataScope, entity.getOwnerId());
        }
        entity.setUpdatedAt(now);
        entity.setName(trimToNull(request.getName()));
        entity.setCustomerId(request.getCustomerId());
        boolean productsChanged = request.getProducts() != null;
        List<OpportunityProductEntity> products = productsChanged
                ? buildProductLines(tenantId, entity.getId(), request.getProducts(), now)
                : null;
        entity.setAmount(resolveAmount(request, products, productsChanged));
        entity.setStage(request.getStage() == null ? OpportunityStage.DISCOVERY : request.getStage());
        entity.setProbability(request.getProbability());
        entity.setExpectedCloseDate(request.getExpectedCloseDate());
        Long targetOwnerId = request.getOwnerId() == null ? operatorId : request.getOwnerId();
        checkDataScope(operatorId, dataScope, targetOwnerId);
        entity.setOwnerId(targetOwnerId);
        entity.setRemark(trimToNull(request.getRemark()));
        if (request.getId() == null) {
            opportunityMapper.insert(entity);
        } else {
            opportunityMapper.updateById(entity);
        }
        if (productsChanged) {
            replaceProductLines(tenantId, entity.getId(), products);
        }
        OpportunityResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillCustomerName(tenantId, response);
        fillProductLines(tenantId, response);
        return response;
    }

    @Transactional
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        OpportunityEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        entity.setUpdatedAt(DateTimes.now());
        opportunityMapper.updateById(entity);
        deleteProductLines(tenantId, id);
    }

    private QueryWrapper<OpportunityEntity> buildQueryWrapper(
            Long tenantId, Long userId, String dataScope, OpportunityQuery query) {
        QueryWrapper<OpportunityEntity> wrapper = new QueryWrapper<OpportunityEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        if ("SELF".equals(dataScope)) {
            wrapper.eq("owner_id", userId);
        }
        if (query.getStage() != null) {
            wrapper.eq("stage", query.getStage().name());
        }
        if (query.getCustomerId() != null) {
            wrapper.eq("customer_id", query.getCustomerId());
        }
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value.like("name", keyword).or().like("remark", keyword));
        }
        return wrapper;
    }

    private OpportunityEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("OPPORTUNITY_001", "商机编号不能为空");
        }
        QueryWrapper<OpportunityEntity> wrapper = new QueryWrapper<OpportunityEntity>();
        wrapper.eq("id", id);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        OpportunityEntity entity = opportunityMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("OPPORTUNITY_002", "商机不存在");
        }
        return entity;
    }

    private CustomerEntity findCustomer(Long tenantId, Long id) {
        QueryWrapper<CustomerEntity> wrapper = new QueryWrapper<CustomerEntity>();
        wrapper.eq("id", id);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        CustomerEntity entity = customerMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("CUSTOMER_002", "客户不存在");
        }
        return entity;
    }

    private ProductEntity findProduct(Long tenantId, Long id) {
        QueryWrapper<ProductEntity> wrapper = new QueryWrapper<ProductEntity>();
        wrapper.eq("id", id);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        ProductEntity entity = productMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("PRODUCT_002", "产品不存在");
        }
        if (!entity.isEnabled()) {
            throw new BusinessException("PRODUCT_004", "产品已停用，不能加入商机");
        }
        return entity;
    }

    private List<OpportunityProductEntity> buildProductLines(
            Long tenantId,
            Long opportunityId,
            List<OpportunityProductSaveRequest> requests,
            LocalDateTime now) {
        List<OpportunityProductEntity> products = new ArrayList<OpportunityProductEntity>();
        if (requests == null) {
            return products;
        }
        for (OpportunityProductSaveRequest request : requests) {
            if (request == null || (request.getProductId() == null && trimToNull(request.getProductName()) == null)) {
                continue;
            }
            ProductEntity product = request.getProductId() == null ? null : findProduct(tenantId, request.getProductId());
            OpportunityProductEntity entity = new OpportunityProductEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setOpportunityId(opportunityId);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entity.setProductId(product == null ? null : product.getId());
            entity.setProductCode(product == null ? null : product.getCode());
            entity.setProductName(resolveProductName(product, request));
            entity.setCategory(product == null ? null : product.getCategory());
            entity.setProductType(product == null ? null : product.getProductType());
            entity.setQuantity(resolveQuantity(request.getQuantity()));
            entity.setUnitPrice(resolveUnitPrice(product, request));
            entity.setDiscountRate(resolveDiscountRate(request.getDiscountRate()));
            entity.setUnit(resolveUnit(product, request));
            entity.setRemark(trimToNull(request.getRemark()));
            entity.setSubtotal(calculateSubtotal(entity));
            products.add(entity);
        }
        return products;
    }

    private BigDecimal resolveAmount(
            OpportunitySaveRequest request, List<OpportunityProductEntity> products, boolean productsChanged) {
        if (!productsChanged || products == null || products.isEmpty()) {
            return request.getAmount();
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OpportunityProductEntity product : products) {
            if (product.getSubtotal() != null) {
                total = total.add(product.getSubtotal());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private void replaceProductLines(Long tenantId, Long opportunityId, List<OpportunityProductEntity> products) {
        deleteProductLines(tenantId, opportunityId);
        if (products == null || products.isEmpty()) {
            return;
        }
        for (OpportunityProductEntity product : products) {
            opportunityProductMapper.insert(product);
        }
    }

    private void deleteProductLines(Long tenantId, Long opportunityId) {
        QueryWrapper<OpportunityProductEntity> wrapper = new QueryWrapper<OpportunityProductEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("opportunity_id", opportunityId);
        wrapper.eq("deleted", false);
        List<OpportunityProductEntity> products = opportunityProductMapper.selectList(wrapper);
        LocalDateTime now = DateTimes.now();
        for (OpportunityProductEntity product : products) {
            product.setDeleted(true);
            product.setUpdatedAt(now);
            opportunityProductMapper.updateById(product);
        }
    }

    private String resolveProductName(ProductEntity product, OpportunityProductSaveRequest request) {
        if (product != null) {
            return product.getName();
        }
        String name = trimToNull(request.getProductName());
        if (name == null) {
            throw new BusinessException("OPPORTUNITY_004", "商机产品名称不能为空");
        }
        return name;
    }

    private BigDecimal resolveQuantity(BigDecimal value) {
        BigDecimal quantity = value == null ? BigDecimal.ONE : value;
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("OPPORTUNITY_005", "产品数量必须大于0");
        }
        return quantity;
    }

    private BigDecimal resolveUnitPrice(ProductEntity product, OpportunityProductSaveRequest request) {
        BigDecimal price = request.getUnitPrice();
        if (price == null && product != null) {
            price = product.getPrice();
        }
        if (price == null) {
            price = BigDecimal.ZERO;
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("OPPORTUNITY_006", "产品单价不能小于0");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveDiscountRate(BigDecimal value) {
        BigDecimal discountRate = value == null ? new BigDecimal("100") : value;
        if (discountRate.compareTo(BigDecimal.ZERO) < 0 || discountRate.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException("OPPORTUNITY_007", "产品折扣必须在0到100之间");
        }
        return discountRate.setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveUnit(ProductEntity product, OpportunityProductSaveRequest request) {
        String unit = trimToNull(request.getUnit());
        if (unit != null) {
            return unit;
        }
        return product == null ? null : product.getUnit();
    }

    private BigDecimal calculateSubtotal(OpportunityProductEntity entity) {
        return entity.getQuantity()
                .multiply(entity.getUnitPrice())
                .multiply(entity.getDiscountRate())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private void checkDataScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_001", "无权访问该数据");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
        response.setProducts(new ArrayList<OpportunityProductResponse>());
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

    private void fillCustomerName(Long tenantId, OpportunityResponse response) {
        List<OpportunityResponse> records = new ArrayList<OpportunityResponse>();
        records.add(response);
        fillCustomerNames(tenantId, records);
    }

    private void fillCustomerNames(Long tenantId, List<OpportunityResponse> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> customerIds = new HashSet<Long>();
        for (OpportunityResponse response : records) {
            if (response.getCustomerId() != null) {
                customerIds.add(response.getCustomerId());
            }
        }
        if (customerIds.isEmpty()) {
            return;
        }
        QueryWrapper<CustomerEntity> wrapper = new QueryWrapper<CustomerEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.in("id", customerIds);
        List<CustomerEntity> customers = customerMapper.selectList(wrapper);
        Map<Long, String> names = new HashMap<Long, String>();
        for (CustomerEntity customer : customers) {
            names.put(customer.getId(), customer.getName());
        }
        for (OpportunityResponse response : records) {
            if (response.getCustomerId() != null) {
                response.setCustomerName(names.get(response.getCustomerId()));
            }
        }
    }

    private void fillProductLines(Long tenantId, OpportunityResponse response) {
        List<OpportunityResponse> records = new ArrayList<OpportunityResponse>();
        records.add(response);
        fillProductLines(tenantId, records);
    }

    private void fillProductLines(Long tenantId, List<OpportunityResponse> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> opportunityIds = new HashSet<Long>();
        for (OpportunityResponse response : records) {
            if (response.getId() != null) {
                opportunityIds.add(response.getId());
            }
        }
        if (opportunityIds.isEmpty()) {
            return;
        }
        QueryWrapper<OpportunityProductEntity> wrapper = new QueryWrapper<OpportunityProductEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.in("opportunity_id", opportunityIds);
        wrapper.orderByAsc("created_at");
        List<OpportunityProductEntity> products = opportunityProductMapper.selectList(wrapper);
        Map<Long, List<OpportunityProductResponse>> productMap =
                new HashMap<Long, List<OpportunityProductResponse>>();
        for (OpportunityProductEntity product : products) {
            List<OpportunityProductResponse> list = productMap.get(product.getOpportunityId());
            if (list == null) {
                list = new ArrayList<OpportunityProductResponse>();
                productMap.put(product.getOpportunityId(), list);
            }
            list.add(toProductResponse(product));
        }
        for (OpportunityResponse response : records) {
            List<OpportunityProductResponse> list = productMap.get(response.getId());
            response.setProducts(list == null ? new ArrayList<OpportunityProductResponse>() : list);
        }
    }

    private OpportunityProductResponse toProductResponse(OpportunityProductEntity entity) {
        OpportunityProductResponse response = new OpportunityProductResponse();
        response.setId(entity.getId());
        response.setProductId(entity.getProductId());
        response.setProductCode(entity.getProductCode());
        response.setProductName(entity.getProductName());
        response.setCategory(entity.getCategory());
        response.setProductType(entity.getProductType());
        response.setQuantity(entity.getQuantity());
        response.setUnitPrice(entity.getUnitPrice());
        response.setDiscountRate(entity.getDiscountRate());
        response.setSubtotal(entity.getSubtotal());
        response.setUnit(entity.getUnit());
        response.setRemark(entity.getRemark());
        return response;
    }
}
