package com.hz.crm.agent.web.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentAssistantConversationResponse {

    private Long id;

    private Long agentId;

    private String title;

    private String sceneCode;

    private String status;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;
}
