package com.hz.crm.agent.runtime.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentSaveRequest {

    private Long id;

    @NotBlank(message = "Agent编码不能为空")
    private String code;

    @NotBlank(message = "Agent名称不能为空")
    private String name;

    private String description;

    private String systemPrompt;

    private Long modelConfigId;

    private String modelProvider;

    private String modelName;

    private String baseUrl;

    private String apiKeyEnv;

    private Integer maxIters;

    private Boolean enabled;
}
