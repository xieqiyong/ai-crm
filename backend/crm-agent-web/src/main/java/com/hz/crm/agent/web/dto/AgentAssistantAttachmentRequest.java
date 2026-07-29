package com.hz.crm.agent.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentAssistantAttachmentRequest {

    private String fileName;

    private String contentType;

    private Long size;

    private String storageKey;

    private String url;
}
