package com.hz.crm.application.product.dto;

import com.hz.crm.domain.product.ProductType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductResponse {

    private Long id;

    private Long tenantId;

    private String code;

    private String name;

    private String category;

    private ProductType productType;

    private BigDecimal price;

    private String unit;

    private Boolean enabled;

    private String description;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
