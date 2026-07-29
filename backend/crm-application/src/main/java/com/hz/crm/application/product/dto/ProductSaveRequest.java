package com.hz.crm.application.product.dto;

import com.hz.crm.domain.product.ProductCategory;
import com.hz.crm.domain.product.ProductType;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductSaveRequest {

    private Long id;

    @NotBlank(message = "产品名称不能为空")
    private String name;

    private ProductCategory category;

    private ProductType productType;

    private BigDecimal price;

    private String unit;

    private Boolean enabled;

    private String description;

    private String remark;
}
