package com.hz.crm.agent.web.dto;

import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiRuntimeProxyResponse {

    private boolean success;

    private String output;

    private List<AgentRuntimeEvent> events = new ArrayList<AgentRuntimeEvent>();

    private String runId;

    private String conversationId;

    private String threadId;

    private boolean checkpointEnabled;

    private boolean traceEnabled;

    private String traceId;
}
