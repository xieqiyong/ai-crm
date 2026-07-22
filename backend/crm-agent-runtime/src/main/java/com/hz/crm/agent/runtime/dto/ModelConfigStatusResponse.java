package com.hz.crm.agent.runtime.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModelConfigStatusResponse {

    private String id;

    private boolean available;

    private String message;
}
