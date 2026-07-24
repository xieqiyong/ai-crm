package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.common.exception.BusinessException;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeModelFactory {

    public OpenAIChatModel build(AgentEntity agent) {
        if (blank(agent.getModelName())) {
            throw new BusinessException("AGENT_005", "模型名称不能为空");
        }
        if (blank(agent.getApiKey())) {
            throw new BusinessException("AGENT_006", "模型密钥不能为空");
        }
        String apiKey = agent.getApiKey().trim();
        if (blank(apiKey)) {
            throw new BusinessException("AGENT_004", "模型密钥未配置");
        }
        OpenAIChatModel.Builder modelBuilder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(agent.getModelName())
                .stream(true);
        String baseUrl = resolveBaseUrl(agent);
        if (!blank(baseUrl)) {
            modelBuilder.baseUrl(baseUrl);
        }
        return modelBuilder.build();
    }

    private String resolveBaseUrl(AgentEntity agent) {
        if (!blank(agent.getBaseUrl())) {
            return trimRightSlash(agent.getBaseUrl().trim());
        }
        if ("DEEPSEEK".equalsIgnoreCase(agent.getModelProvider())) {
            return "https://api.deepseek.com/v1";
        }
        if ("DASHSCOPE".equalsIgnoreCase(agent.getModelProvider())) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return "";
    }

    private String trimRightSlash(String value) {
        String text = value;
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
