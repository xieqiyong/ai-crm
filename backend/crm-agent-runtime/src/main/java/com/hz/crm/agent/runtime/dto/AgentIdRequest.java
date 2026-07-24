package com.hz.crm.agent.runtime.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentIdRequest {

    @NotNull(message = "智能体编号不能为空")
    private Long agentId;
}
