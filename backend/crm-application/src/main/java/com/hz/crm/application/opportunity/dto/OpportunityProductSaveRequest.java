package com.hz.crm.application.opportunity.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpportunityProductSaveRequest {

    private Long id;

    private Long productId;

    private String productName;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal discountRate;

    private String unit;

    private String remark;
}
