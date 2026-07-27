package com.hz.crm.agent.web.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class AiRuntimeProxyProperties {

    @Value("${crm.ai.runtime.url:http://localhost:8001}")
    private String url;

    @Value("${crm.ai.runtime.internal-token:}")
    private String internalToken;

    @Value("${crm.ai.runtime.timeout-ms:90000}")
    private Integer timeoutMs;
}
