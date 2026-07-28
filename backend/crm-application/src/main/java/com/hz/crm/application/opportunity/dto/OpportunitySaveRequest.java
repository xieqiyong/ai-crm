package com.hz.crm.application.opportunity.dto;

import com.hz.crm.domain.opportunity.OpportunityStage;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpportunitySaveRequest {

    private Long id;

    @NotBlank(message = "商机名称不能为空")
    private String name;

    private Long customerId;

    private BigDecimal amount;

    private OpportunityStage stage;

    private Integer probability;

    private LocalDate expectedCloseDate;

    private Long ownerId;

    private String remark;

    private List<OpportunityProductSaveRequest> products;
}
