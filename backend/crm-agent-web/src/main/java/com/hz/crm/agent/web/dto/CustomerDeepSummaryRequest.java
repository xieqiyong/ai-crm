package com.hz.crm.agent.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerDeepSummaryRequest {

    @NotNull(message = "客户编号不能为空")
    private Long customerId;

    private String instruction;
}
