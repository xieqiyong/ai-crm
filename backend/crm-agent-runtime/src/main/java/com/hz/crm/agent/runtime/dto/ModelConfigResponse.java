package com.hz.crm.agent.runtime.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModelConfigResponse {

    private Long id;

    private String provider;

    private String name;

    private String modelName;

    private String baseUrl;

    private String apiKeyEnv;

    private String remark;

    private boolean defaultConfig;

    private boolean enabled;

    private boolean apiKeyConfigured;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
