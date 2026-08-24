package com.hz.crm.application.product;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.product.ProductEntity;
import com.hz.crm.domain.product.mapper.ProductMapper;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductReferenceResolver {

    @Autowired
    private ProductMapper productMapper;

    public ProductEntity require(Long tenantId, Long productId) {
        if (productId == null) {
            throw new BusinessException("PRODUCT_REFERENCE_001", "请选择关联产品");
        }
        ProductEntity product = findOne(tenantId, productId);
        if (product == null) {
            throw new BusinessException("PRODUCT_REFERENCE_002", "关联产品不存在或已删除");
        }
        return product;
    }

    public String resolveName(Long tenantId, Long productId) {
        if (productId == null) {
            return null;
        }
        ProductEntity product = findOne(tenantId, productId);
        return product == null ? null : product.getName();
    }

    public Map<Long, String> resolveNames(Long tenantId, Collection<Long> productIds) {
        Map<Long, String> names = new HashMap<Long, String>();
        if (productIds == null || productIds.isEmpty()) {
            return names;
        }
        QueryWrapper<ProductEntity> wrapper = new QueryWrapper<ProductEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.in("id", productIds);
        List<ProductEntity> products = productMapper.selectList(wrapper);
        for (ProductEntity product : products) {
            names.put(product.getId(), product.getName());
        }
        return names;
    }

    private ProductEntity findOne(Long tenantId, Long productId) {
        QueryWrapper<ProductEntity> wrapper = new QueryWrapper<ProductEntity>();
        wrapper.eq("id", productId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.last("limit 1");
        return productMapper.selectOne(wrapper);
    }
}
