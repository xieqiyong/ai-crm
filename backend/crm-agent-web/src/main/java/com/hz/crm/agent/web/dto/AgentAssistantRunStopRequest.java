package com.hz.crm.agent.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentAssistantRunStopRequest {

    @NotBlank(message = "运行请求编号不能为空")
    private String requestId;
}
