package com.hz.crm.agent.runtime.core;

import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRuntimeRequest {

    private Long tenantId;

    private Long userId;

    private AgentEntity agent;

    private Long runId;

    private Long conversationId;

    private List<AgentMcpEntity> mcps = new ArrayList<AgentMcpEntity>();

    private List<AgentSkillEntity> skills = new ArrayList<AgentSkillEntity>();

    private String message;

    private String sessionId;

    private String injectedPrompt;

    private String sceneCode;

    private String businessType;

    private String businessId;

    private Map<String, Object> context = new HashMap<String, Object>();
}
