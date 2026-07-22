package com.hz.crm.agent.runtime.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModelConfigDebugRequest {

    private Long id;

    private String prompt;

    private Integer timeoutSeconds;
}
