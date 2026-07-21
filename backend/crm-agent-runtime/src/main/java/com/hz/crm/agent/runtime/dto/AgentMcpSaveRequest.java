package com.hz.crm.agent.runtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentMcpSaveRequest {

    private Long id;

    @NotNull(message = "Agent编号不能为空")
    private Long agentId;

    @NotBlank(message = "MCP名称不能为空")
    private String name;

    @NotBlank(message = "MCP传输类型不能为空")
    private String transportType;

    private String endpoint;

    private String command;

    private String argumentsJson;

    private String headersJson;

    private Boolean enabled;
}
