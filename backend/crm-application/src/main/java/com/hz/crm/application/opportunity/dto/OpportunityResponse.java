package com.hz.crm.application.opportunity.dto;

import com.hz.crm.domain.opportunity.OpportunityStage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpportunityResponse {

    private Long id;

    private String tenantId;

    private String name;

    private Long customerId;

    private BigDecimal amount;

    private OpportunityStage stage;

    private Integer probability;

    private LocalDate expectedCloseDate;

    private Long ownerId;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
