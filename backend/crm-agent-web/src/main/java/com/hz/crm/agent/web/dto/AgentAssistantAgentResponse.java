package com.hz.crm.agent.web.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentAssistantAgentResponse {

    private Long id;

    private String code;

    private String sceneCode;

    private String sceneName;

    private String name;

    private String description;

    private String modelProvider;

    private String modelName;

    private Integer maxIters;

    private boolean enabled;

    private Long conversationCount;

    private LocalDateTime lastMessageAt;
}
