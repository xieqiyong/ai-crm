package com.hz.crm.agent.web.dto;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketingAssistantActionResponse {

    private String code;

    private String title;

    private String description;

    private String targetRoute;

    private Map<String, Object> payload = new HashMap<String, Object>();
}
