package com.hz.crm.agent.runtime.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModelConfigRequest {

    private Long id;

    private String provider;

    private String name;

    private String modelName;

    private String baseUrl;

    private String apiKeyEnv;

    private String remark;

    private Boolean defaultConfig;

    private Boolean enabled;
}
