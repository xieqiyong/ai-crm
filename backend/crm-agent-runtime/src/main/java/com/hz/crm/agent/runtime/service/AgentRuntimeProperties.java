package com.hz.crm.agent.runtime.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class AgentRuntimeProperties {

    @Value("${crm.agent.workspace:./data/agent-runtime}")
    private String workspace;

    @Value("${crm.agent.mcp.timeout-seconds:60}")
    private Long mcpTimeoutSeconds;

    @Value("${crm.agent.prompt.base:你是智能营销管理系统的AI助手，只能基于真实业务数据回答，不能构造不存在的数据。}")
    private String basePrompt;

    @Value("${crm.agent.token.daily-limit:100000}")
    private Long tokenDailyLimit;

    @Value("${crm.agent.token.reserve-output-tokens:2048}")
    private Long tokenReserveOutputTokens;
}
