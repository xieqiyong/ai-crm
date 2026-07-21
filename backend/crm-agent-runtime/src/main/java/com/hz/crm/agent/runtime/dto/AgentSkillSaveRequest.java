package com.hz.crm.agent.runtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentSkillSaveRequest {

    private Long id;

    @NotNull(message = "Agent编号不能为空")
    private Long agentId;

    @NotBlank(message = "Skill标识不能为空")
    private String skillKey;

    @NotBlank(message = "Skill名称不能为空")
    private String name;

    private String content;

    private Boolean enabled;
}
