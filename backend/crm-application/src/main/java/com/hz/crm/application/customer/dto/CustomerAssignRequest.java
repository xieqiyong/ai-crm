package com.hz.crm.application.customer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerAssignRequest {

    @NotNull(message = "客户编号不能为空")
    private Long id;

    @NotNull(message = "负责人不能为空")
    private Long ownerId;
}
