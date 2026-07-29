package com.hz.crm.application.lead.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadAssignRequest {

    @NotNull(message = "线索编号不能为空")
    private Long id;

    @NotNull(message = "负责人不能为空")
    private Long ownerId;
}
