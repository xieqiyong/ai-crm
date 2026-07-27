package com.hz.crm.agent.web.dto;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketingAssistantChatRequest {

    private String message;

    private String routeKey;

    private String businessType;

    private String businessId;

    private Map<String, Object> context = new HashMap<String, Object>();
}
