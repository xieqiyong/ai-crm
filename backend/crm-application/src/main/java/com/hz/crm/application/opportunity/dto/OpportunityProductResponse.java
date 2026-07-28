package com.hz.crm.application.opportunity.dto;

import com.hz.crm.domain.product.ProductType;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpportunityProductResponse {

    private Long id;

    private Long productId;

    private String productCode;

    private String productName;

    private String category;

    private ProductType productType;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal discountRate;

    private BigDecimal subtotal;

    private String unit;

    private String remark;
}
