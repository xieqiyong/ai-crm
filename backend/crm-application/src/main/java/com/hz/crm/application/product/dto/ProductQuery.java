package com.hz.crm.application.product.dto;

import com.hz.crm.common.api.PageQuery;
import com.hz.crm.domain.product.ProductCategory;
import com.hz.crm.domain.product.ProductType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductQuery extends PageQuery {

    private String keyword;

    private ProductCategory category;

    private ProductType productType;

    private Boolean enabled;
}
