package com.hz.crm.application.product;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.product.dto.ProductQuery;
import com.hz.crm.application.product.dto.ProductResponse;
import com.hz.crm.application.product.dto.ProductSaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.product.ProductCategory;
import com.hz.crm.domain.product.ProductEntity;
import com.hz.crm.domain.product.ProductType;
import com.hz.crm.domain.product.mapper.ProductMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductApplicationService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(readOnly = true)
    public PageData<ProductResponse> page(Long tenantId, ProductQuery query) {
        ProductQuery safeQuery = query == null ? new ProductQuery() : query;
        long total = productMapper.selectCount(buildQueryWrapper(tenantId, safeQuery));
        QueryWrapper<ProductEntity> wrapper = buildQueryWrapper(tenantId, safeQuery);
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        int offset = (pageNo - 1) * pageSize;
        wrapper.orderByDesc("created_at").last("limit " + pageSize + " offset " + offset);
        List<ProductEntity> entities = productMapper.selectList(wrapper);
        List<ProductResponse> records = new ArrayList<ProductResponse>();
        for (ProductEntity entity : entities) {
            records.add(toResponse(entity));
        }
        return PageData.of(total, pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public ProductResponse detail(Long tenantId, Long id) {
        return toResponse(findOne(tenantId, id));
    }

    @Transactional
    public ProductResponse save(Long tenantId, ProductSaveRequest request) {
        if (request == null || trimToNull(request.getName()) == null) {
            throw new BusinessException("PRODUCT_003", "产品名称不能为空");
        }
        ProductEntity entity;
        LocalDateTime now = DateTimes.now();
        if (request.getId() == null) {
            entity = new ProductEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setCode(buildCode(entity.getId()));
            entity.setCreatedAt(now);
        } else {
            entity = findOne(tenantId, request.getId());
        }
        entity.setUpdatedAt(now);
        entity.setName(trimToNull(request.getName()));
        entity.setCategory(request.getCategory() == null ? ProductCategory.OTHER : request.getCategory());
        entity.setProductType(request.getProductType() == null ? ProductType.STANDARD : request.getProductType());
        entity.setPrice(resolvePrice(request.getPrice()));
        entity.setUnit(trimToNull(request.getUnit()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setRemark(trimToNull(request.getRemark()));
        if (request.getId() == null) {
            productMapper.insert(entity);
        } else {
            productMapper.updateById(entity);
        }
        return toResponse(entity);
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        ProductEntity entity = findOne(tenantId, id);
        entity.setDeleted(true);
        entity.setUpdatedAt(DateTimes.now());
        productMapper.updateById(entity);
    }

    private QueryWrapper<ProductEntity> buildQueryWrapper(Long tenantId, ProductQuery query) {
        QueryWrapper<ProductEntity> wrapper = new QueryWrapper<ProductEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        if (query.getEnabled() != null) {
            wrapper.eq("enabled", query.getEnabled());
        }
        if (query.getProductType() != null) {
            wrapper.eq("product_type", query.getProductType().name());
        }
        ProductCategory category = query.getCategory();
        if (category != null) {
            wrapper.eq("category", category.name());
        }
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .like("name", keyword)
                    .or()
                    .like("code", keyword)
                    .or()
                    .like("category", keyword)
                    .or()
                    .like("description", keyword));
        }
        return wrapper;
    }

    private ProductEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("PRODUCT_001", "产品编号不能为空");
        }
        QueryWrapper<ProductEntity> wrapper = new QueryWrapper<ProductEntity>();
        wrapper.eq("id", id);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        ProductEntity entity = productMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("PRODUCT_002", "产品不存在");
        }
        return entity;
    }

    private String buildCode(Long id) {
        return "P" + String.valueOf(id);
    }

    private BigDecimal resolvePrice(BigDecimal price) {
        if (price == null) {
            return null;
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("PRODUCT_005", "产品价格不能小于0");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private ProductResponse toResponse(ProductEntity entity) {
        ProductResponse response = new ProductResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setCode(entity.getCode());
        response.setName(entity.getName());
        response.setCategory(entity.getCategory());
        response.setProductType(entity.getProductType());
        response.setPrice(entity.getPrice());
        response.setUnit(entity.getUnit());
        response.setEnabled(entity.isEnabled());
        response.setDescription(entity.getDescription());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
