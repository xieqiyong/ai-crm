package com.hz.crm.agent.runtime.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModelConfigDebugResponse {

    private String id;

    private boolean success;

    private String message;

    private String output;

    private long elapsedMs;
}
