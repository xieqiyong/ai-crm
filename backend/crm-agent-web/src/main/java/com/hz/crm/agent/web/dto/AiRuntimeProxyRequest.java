package com.hz.crm.agent.web.dto;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiRuntimeProxyRequest {

    private Long agentId;

    private Long runId;

    private Long conversationId;

    private String sceneCode;

    private String message;

    private String sessionId;

    private String injectedPrompt;

    private String businessType;

    private String businessId;

    private Map<String, Object> context = new HashMap<String, Object>();
}
