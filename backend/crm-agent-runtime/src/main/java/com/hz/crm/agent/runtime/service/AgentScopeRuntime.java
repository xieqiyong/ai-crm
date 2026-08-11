package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.core.AgentRuntimeEngine;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.agent.runtime.workflow.AgentWorkflowEngine;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentScopeRuntime implements AgentRuntimeEngine {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeRuntime.class);

    @Autowired
    private AgentRuntimeWorkspaceService agentRuntimeWorkspaceService;

    @Autowired
    private AgentRuntimeMcpMountService agentRuntimeMcpMountService;

    @Autowired
    private AgentRuntimeSkillMountService agentRuntimeSkillMountService;

    @Autowired
    private AgentRuntimePromptService agentRuntimePromptService;

    @Autowired
    private AgentRuntimeContextFactory agentRuntimeContextFactory;

    @Autowired
    private AgentRuntimeModelFactory agentRuntimeModelFactory;

    @Autowired
    private AgentRuntimeEventMapper agentRuntimeEventMapper;

    @Autowired
    private AgentWorkflowEngine agentWorkflowEngine;

    @Autowired(required = false)
    private List<AgentRuntimeToolProvider> agentRuntimeToolProviders = new ArrayList<AgentRuntimeToolProvider>();

    @Override
    public Flux<AgentRuntimeEvent> run(AgentRuntimeRequest request) {
        return agentWorkflowEngine.run(request, this::runAgentScope);
    }

    private Flux<AgentRuntimeEvent> runAgentScope(AgentRuntimeRequest request) {
        return Flux.defer(() -> {
            long start = System.currentTimeMillis();
            try {
                RuntimeHolder holder = buildRuntime(request);
                return holder.getAgent()
                        .streamEvents(new UserMessage(request.getMessage()), holder.getRuntimeContext())
                        .map(agentRuntimeEventMapper::toRuntimeEvent)
                        .doOnSubscribe(subscription -> log.info(
                                "AgentScope流开始，tenantId={}，userId={}，sceneCode={}，runId={}",
                                request.getTenantId(), request.getUserId(), request.getSceneCode(), request.getRunId()))
                        .doFinally(signalType -> {
                            log.info(
                                    "AgentScope流结束，tenantId={}，userId={}，sceneCode={}，runId={}，信号={}，耗时={}ms",
                                    request.getTenantId(),
                                    request.getUserId(),
                                    request.getSceneCode(),
                                    request.getRunId(),
                                    signalType,
                                    Long.valueOf(System.currentTimeMillis() - start));
                            holder.getAgent().close();
                        });
            } catch (RuntimeException ex) {
                log.warn(
                        "AgentScope流启动失败，tenantId={}，userId={}，sceneCode={}，runId={}，耗时={}ms",
                        request.getTenantId(),
                        request.getUserId(),
                        request.getSceneCode(),
                        request.getRunId(),
                        Long.valueOf(System.currentTimeMillis() - start),
                        ex);
                return Flux.error(ex);
            }
        });
    }

    private RuntimeHolder buildRuntime(AgentRuntimeRequest request) {
        long start = System.currentTimeMillis();
        AgentEntity sceneAgent = request.getAgent();
        if (sceneAgent == null) {
            throw new BusinessException("AGENT_RUNTIME_007", "场景智能体不能为空");
        }
        Toolkit toolkit = new Toolkit();
        long toolStart = System.currentTimeMillis();
        registerBuiltinTools(toolkit, request);
        long builtinToolMs = System.currentTimeMillis() - toolStart;
        long mcpStart = System.currentTimeMillis();
        agentRuntimeMcpMountService.mount(toolkit, request.getMcps());
        long mcpMs = System.currentTimeMillis() - mcpStart;
        long workspaceStart = System.currentTimeMillis();
        Path agentWorkspace = agentRuntimeWorkspaceService.resolveAgentWorkspace(request);
        long workspaceMs = System.currentTimeMillis() - workspaceStart;
        long skillStart = System.currentTimeMillis();
        Path skillRoot = agentRuntimeSkillMountService.materialize(agentWorkspace, request.getSkills());
        long skillMs = System.currentTimeMillis() - skillStart;
        long buildStart = System.currentTimeMillis();
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(sceneAgent.getName())
                .sysPrompt(agentRuntimePromptService.render(
                        sceneAgent.getSystemPrompt(), request.getInjectedPrompt(), request.getContext()))
                .model(agentRuntimeModelFactory.build(sceneAgent))
                .toolkit(toolkit)
                .workspace(agentWorkspace)
                .projectGlobalSkillsDir(skillRoot)
                .maxIters(resolveMaxIters(request));
        RuntimeHolder holder = new RuntimeHolder();
        holder.setAgent(builder.build());
        holder.setRuntimeContext(agentRuntimeContextFactory.build(request));
        long buildMs = System.currentTimeMillis() - buildStart;
        log.info(
                "Agent运行时构建完成，tenantId={}，userId={}，sceneCode={}，runId={}，agentId={}，mcp数量={}，skill数量={}，内置工具={}ms，MCP挂载={}ms，工作目录={}ms，Skill物料={}ms，模型构建={}ms，总耗时={}ms",
                request.getTenantId(),
                request.getUserId(),
                request.getSceneCode(),
                request.getRunId(),
                sceneAgent.getId(),
                Integer.valueOf(request.getMcps() == null ? 0 : request.getMcps().size()),
                Integer.valueOf(request.getSkills() == null ? 0 : request.getSkills().size()),
                Long.valueOf(builtinToolMs),
                Long.valueOf(mcpMs),
                Long.valueOf(workspaceMs),
                Long.valueOf(skillMs),
                Long.valueOf(buildMs),
                Long.valueOf(System.currentTimeMillis() - start));
        return holder;
    }

    private int resolveMaxIters(AgentRuntimeRequest request) {
        Integer maxIters = request.getAgent().getMaxIters();
        if (maxIters == null || maxIters.intValue() <= 0) {
            return 8;
        }
        return maxIters.intValue();
    }

    private void registerBuiltinTools(Toolkit toolkit, AgentRuntimeRequest request) {
        Set<String> registeredNames = new HashSet<String>();
        for (AgentRuntimeToolProvider provider : safeToolProviders()) {
            List<AgentTool> tools = provider.resolveTools(request);
            if (tools == null || tools.isEmpty()) {
                continue;
            }
            for (AgentTool tool : tools) {
                if (tool == null || tool.getName() == null || registeredNames.contains(tool.getName())) {
                    continue;
                }
                toolkit.registerAgentTool(tool);
                registeredNames.add(tool.getName());
            }
        }
    }

    private List<AgentRuntimeToolProvider> safeToolProviders() {
        if (agentRuntimeToolProviders == null) {
            return new ArrayList<AgentRuntimeToolProvider>();
        }
        return agentRuntimeToolProviders;
    }

    @lombok.Getter
    @lombok.Setter
    private static class RuntimeHolder {

        private HarnessAgent agent;

        private RuntimeContext runtimeContext;
    }
}
