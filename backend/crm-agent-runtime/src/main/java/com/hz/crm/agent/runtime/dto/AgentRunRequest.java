package com.hz.crm.agent.runtime.dto;

import java.util.HashMap;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRunRequest {

    @NotNull(message = "Agent编号不能为空")
    private Long agentId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    private String sessionId;

    private String injectedPrompt;

    private String sceneCode;

    private String businessType;

    private String businessId;

    private Map<String, Object> context = new HashMap<String, Object>();
}
