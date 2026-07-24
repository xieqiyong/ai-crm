package com.hz.crm.agent.runtime.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentSaveRequest {

    private Long id;

    private String code;

    private String sceneCode;

    private String sceneName;

    private String name;

    private String description;

    private String systemPrompt;

    private Long modelConfigId;

    private String modelProvider;

    private String modelName;

    private String baseUrl;

    private String apiKey;

    private Integer maxIters;

    private String extraConfigJson;

    private String remark;

    private Boolean enabled;
}
