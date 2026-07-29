package com.hz.crm.agent.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentAssistantMessagesRequest {

    @NotNull(message = "会话编号不能为空")
    private Long conversationId;
}
