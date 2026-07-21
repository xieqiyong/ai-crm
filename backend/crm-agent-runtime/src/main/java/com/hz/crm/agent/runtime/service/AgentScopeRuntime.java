package com.hz.crm.agent.runtime.service;

import com.alibaba.fastjson2.TypeReference;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import com.hz.crm.agent.runtime.dto.AgentRunRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.json.Jsons;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentScopeRuntime {

    @Value("${crm.agent.workspace:./data/agent-runtime}")
    private String workspace;

    public Flux<AgentRuntimeEvent> run(
            String tenantId,
            Long userId,
            AgentEntity agent,
            List<AgentMcpEntity> mcps,
            List<AgentSkillEntity> skills,
            AgentRunRequest request) {
        return Flux.defer(() -> {
            try {
                RuntimeHolder holder = buildRuntime(tenantId, userId, agent, mcps, skills, request);
                return holder.getAgent()
                        .streamEvents(request.getMessage(), holder.getRuntimeContext())
                        .map(this::toRuntimeEvent)
                        .doFinally(signalType -> holder.getAgent().close());
            } catch (RuntimeException ex) {
                return Flux.error(ex);
            }
        });
    }

    private RuntimeHolder buildRuntime(
            String tenantId,
            Long userId,
            AgentEntity agent,
            List<AgentMcpEntity> mcps,
            List<AgentSkillEntity> skills,
            AgentRunRequest request) {
        String apiKey = System.getenv(agent.getApiKeyEnv());
        if (apiKey == null || apiKey.trim().length() == 0) {
            throw new BusinessException("AGENT_004", "模型密钥环境变量未配置");
        }
        Toolkit toolkit = new Toolkit();
        mountMcps(toolkit, mcps);
        Path agentWorkspace = Paths.get(workspace, String.valueOf(agent.getId()));
        Path skillRoot = materializeSkills(agentWorkspace, skills);
        OpenAIChatModel.Builder modelBuilder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(agent.getModelName())
                .stream(true);
        if (agent.getBaseUrl() != null && agent.getBaseUrl().trim().length() > 0) {
            modelBuilder.baseUrl(agent.getBaseUrl());
        }
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .agentId(String.valueOf(agent.getId()))
                .name(agent.getName())
                .description(agent.getDescription())
                .sysPrompt(renderPrompt(agent.getSystemPrompt(), request))
                .model(modelBuilder.build())
                .toolkit(toolkit)
                .workspace(agentWorkspace)
                .projectGlobalSkillsDir(skillRoot)
                .skillsEnabled(true)
                .maxIters(agent.getMaxIters() == null ? 8 : agent.getMaxIters());
        List<String> skillKeys = enabledSkillKeys(skills);
        if (!skillKeys.isEmpty()) {
            builder.enableSkills(skillKeys.toArray(new String[skillKeys.size()]));
        }
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .sessionId(resolveSessionId(request))
                .userId(String.valueOf(userId))
                .put("tenantId", tenantId)
                .putAll(resolveContext(request))
                .build();
        RuntimeHolder holder = new RuntimeHolder();
        holder.setAgent(builder.build());
        holder.setRuntimeContext(runtimeContext);
        return holder;
    }

    private void mountMcps(Toolkit toolkit, List<AgentMcpEntity> mcps) {
        for (AgentMcpEntity mcp : mcps) {
            McpClientBuilder builder = McpClientBuilder.create(mcp.getName());
            Map<String, String> headers = parseStringMap(mcp.getHeadersJson());
            List<String> args = parseStringList(mcp.getArgumentsJson());
            if ("SSE".equalsIgnoreCase(mcp.getTransportType())) {
                builder.sseTransport(mcp.getEndpoint());
            } else if ("STREAMABLE_HTTP".equalsIgnoreCase(mcp.getTransportType())) {
                builder.streamableHttpTransport(mcp.getEndpoint());
            } else if ("STDIO".equalsIgnoreCase(mcp.getTransportType())) {
                builder.stdioTransport(mcp.getCommand(), args, headers);
            } else {
                throw new BusinessException("AGENT_MCP_002", "MCP传输类型不支持");
            }
            if (!headers.isEmpty() && !"STDIO".equalsIgnoreCase(mcp.getTransportType())) {
                builder.headers(headers);
            }
            builder.timeout(Duration.ofSeconds(60L));
            toolkit.registerMcpClient(builder.buildSync()).block();
        }
    }

    private Path materializeSkills(Path agentWorkspace, List<AgentSkillEntity> skills) {
        Path skillRoot = agentWorkspace.resolve("skills");
        try {
            Files.createDirectories(skillRoot);
            for (AgentSkillEntity skill : skills) {
                if (skill.getContent() == null || skill.getContent().trim().length() == 0) {
                    continue;
                }
                String dirName = skill.getSkillKey().replaceAll("[^a-zA-Z0-9_-]", "_");
                Path skillDir = skillRoot.resolve(dirName);
                Files.createDirectories(skillDir);
                Files.write(skillDir.resolve("SKILL.md"), skill.getContent().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            throw new BusinessException("AGENT_SKILL_002", "Skill挂载失败");
        }
        return skillRoot;
    }

    private String renderPrompt(String systemPrompt, AgentRunRequest request) {
        StringBuilder builder = new StringBuilder();
        if (systemPrompt != null) {
            builder.append(systemPrompt);
        }
        if (request.getInjectedPrompt() != null && request.getInjectedPrompt().trim().length() > 0) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(request.getInjectedPrompt());
        }
        Map<String, Object> context = resolveContext(request);
        String text = builder.toString();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            text = text.replace(placeholder, entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return text;
    }

    private AgentRuntimeEvent toRuntimeEvent(AgentEvent event) {
        AgentRuntimeEvent runtimeEvent = new AgentRuntimeEvent();
        runtimeEvent.setId(event.getId());
        runtimeEvent.setType(event.getType().getValue());
        runtimeEvent.setMetadata(event.getMetadata());
        if (event instanceof TextBlockDeltaEvent) {
            runtimeEvent.setContent(((TextBlockDeltaEvent) event).getDelta());
        } else if (event instanceof ThinkingBlockDeltaEvent) {
            runtimeEvent.setContent(((ThinkingBlockDeltaEvent) event).getDelta());
        } else if (event instanceof ToolCallStartEvent) {
            runtimeEvent.setToolName(((ToolCallStartEvent) event).getToolCallName());
        } else if (event instanceof ToolCallEndEvent) {
            runtimeEvent.setToolName(((ToolCallEndEvent) event).getToolCallName());
        } else if (event instanceof AgentResultEvent) {
            AgentResultEvent resultEvent = (AgentResultEvent) event;
            if (resultEvent.getResult() != null) {
                runtimeEvent.setContent(resultEvent.getResult().getTextContent());
            }
        }
        return runtimeEvent;
    }

    private String resolveSessionId(AgentRunRequest request) {
        if (request.getSessionId() == null || request.getSessionId().trim().length() == 0) {
            return "default";
        }
        return request.getSessionId();
    }

    private Map<String, Object> resolveContext(AgentRunRequest request) {
        if (request.getContext() == null) {
            return new HashMap<String, Object>();
        }
        return request.getContext();
    }

    private List<String> enabledSkillKeys(List<AgentSkillEntity> skills) {
        List<String> keys = new ArrayList<String>();
        for (AgentSkillEntity skill : skills) {
            if (skill.getSkillKey() != null && skill.getSkillKey().trim().length() > 0) {
                keys.add(skill.getSkillKey());
            }
        }
        return keys;
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

    @lombok.Getter
    @lombok.Setter
    private static class RuntimeHolder {

        private HarnessAgent agent;

        private RuntimeContext runtimeContext;
    }
}
