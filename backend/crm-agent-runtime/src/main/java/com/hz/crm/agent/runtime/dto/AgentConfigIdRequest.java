package com.hz.crm.agent.runtime.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentConfigIdRequest {

    @NotNull(message = "配置编号不能为空")
    private Long id;
}
