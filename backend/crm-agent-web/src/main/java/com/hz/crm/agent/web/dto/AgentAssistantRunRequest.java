package com.hz.crm.agent.web.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentAssistantRunRequest {

    @NotBlank(message = "运行请求编号不能为空")
    private String requestId;

    @NotNull(message = "智能体编号不能为空")
    private Long agentId;

    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    private List<AgentAssistantAttachmentRequest> attachments = new ArrayList<AgentAssistantAttachmentRequest>();
}
