package com.hz.crm.agent.runtime.service;

import com.alibaba.fastjson2.TypeReference;
import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.json.Jsons;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeMcpMountService {

    @Autowired
    private AgentRuntimeProperties agentRuntimeProperties;

    public void mount(Toolkit toolkit, List<AgentMcpEntity> mcps) {
        for (AgentMcpEntity mcp : safeMcps(mcps)) {
            McpClientBuilder builder = buildMcpClient(mcp);
            builder.timeout(Duration.ofSeconds(resolveTimeoutSeconds()));
            toolkit.registerMcpClient(builder.buildSync()).block();
        }
    }

    private McpClientBuilder buildMcpClient(AgentMcpEntity mcp) {
        McpClientBuilder builder = McpClientBuilder.create(mcp.getName());
        String transportType = mcp.getTransportType();
        Map<String, String> headers = parseStringMap(mcp.getHeadersJson());
        List<String> args = parseStringList(mcp.getArgumentsJson());
        if ("SSE".equalsIgnoreCase(transportType)) {
            require(mcp.getEndpoint(), "MCP地址不能为空");
            builder.sseTransport(mcp.getEndpoint());
        } else if ("STREAMABLE_HTTP".equalsIgnoreCase(transportType)) {
            require(mcp.getEndpoint(), "MCP地址不能为空");
            builder.streamableHttpTransport(mcp.getEndpoint());
        } else if ("STDIO".equalsIgnoreCase(transportType)) {
            require(mcp.getCommand(), "MCP命令不能为空");
            builder.stdioTransport(mcp.getCommand(), args, headers);
        } else {
            throw new BusinessException("AGENT_MCP_002", "MCP传输类型不支持");
        }
        if (!headers.isEmpty() && !"STDIO".equalsIgnoreCase(transportType)) {
            builder.headers(headers);
        }
        return builder;
    }

    private long resolveTimeoutSeconds() {
        Long timeout = agentRuntimeProperties.getMcpTimeoutSeconds();
        if (timeout == null || timeout.longValue() <= 0L) {
            return 60L;
        }
        return timeout.longValue();
    }

    private void require(String value, String message) {
        if (value == null || value.trim().length() == 0) {
            throw new BusinessException("AGENT_MCP_003", message);
        }
    }

    private List<AgentMcpEntity> safeMcps(List<AgentMcpEntity> mcps) {
        if (mcps == null) {
            return new ArrayList<AgentMcpEntity>();
        }
        return mcps;
    }

    private Map<String, String> parseStringMap(String text) {
        Map<String, String> value = Jsons.parseObject(text, new TypeReference<Map<String, String>>() {});
        if (value == null) {
            return new HashMap<String, String>();
        }
        return value;
    }

    private List<String> parseStringList(String text) {
        List<String> value = Jsons.parseObject(text, new TypeReference<List<String>>() {});
        if (value == null) {
            return new ArrayList<String>();
        }
        return value;
    }
}
