package com.hz.crm.activity.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkflowStartRequest {

    private String definitionCode;

    private String businessType;

    private Long businessId;
}
