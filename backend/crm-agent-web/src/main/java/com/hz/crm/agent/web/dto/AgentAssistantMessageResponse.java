package com.hz.crm.agent.web.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentAssistantMessageResponse {

    private Long runId;

    private String role;

    private String content;

    private String status;

    private LocalDateTime createdAt;

    private List<AgentAssistantAttachmentRequest> attachments = new ArrayList<AgentAssistantAttachmentRequest>();
}
