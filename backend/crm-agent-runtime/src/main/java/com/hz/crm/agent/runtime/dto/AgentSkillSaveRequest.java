package com.hz.crm.agent.runtime.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentSkillSaveRequest {

    private Long id;

    @NotNull(message = "Agent编号不能为空")
    private Long agentId;

    private String skillKey;

    private String name;

    private String content;

    private Boolean enabled;
}
