package com.hz.crm.application.opportunity.dto;

import com.hz.crm.domain.opportunity.OpportunityStage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpportunityResponse {

    private Long id;

    private Long tenantId;

    private String name;

    private Long customerId;

    private String customerName;

    private BigDecimal amount;

    private OpportunityStage stage;

    private Integer probability;

    private LocalDate expectedCloseDate;

    private Long ownerId;

    private String ownerName;

    private List<OpportunityProductResponse> products;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
