package com.hz.crm.agent.runtime.dto;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRuntimeEvent {

    private String id;

    private String type;

    private String content;

    private String toolName;

    private Map<String, Object> metadata = new HashMap<String, Object>();
}
