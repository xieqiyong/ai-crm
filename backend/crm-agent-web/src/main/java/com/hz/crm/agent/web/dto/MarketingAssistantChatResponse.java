package com.hz.crm.agent.web.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketingAssistantChatResponse {

    private String scenario;

    private String title;

    private String reply;

    private List<String> suggestions = new ArrayList<String>();

    private List<MarketingAssistantActionResponse> quickActions = new ArrayList<MarketingAssistantActionResponse>();

    private Map<String, Object> metrics = new HashMap<String, Object>();
}
