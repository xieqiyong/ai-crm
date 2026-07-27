package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.core.AgentRuntimeEngine;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.agent.runtime.workflow.AgentWorkflowEngine;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentScopeRuntime implements AgentRuntimeEngine {

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
    private AgentRuntimeSceneService agentRuntimeSceneService;

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
            try {
                RuntimeHolder holder = buildRuntime(request);
                return holder.getAgent()
                        .streamEvents(new UserMessage(request.getMessage()), holder.getRuntimeContext())
                        .map(agentRuntimeEventMapper::toRuntimeEvent)
                        .doFinally(signalType -> holder.getAgent().close());
            } catch (RuntimeException ex) {
                return Flux.error(ex);
            }
        });
    }

    private RuntimeHolder buildRuntime(AgentRuntimeRequest request) {
        AgentEntity sceneAgent = agentRuntimeSceneService.prepare(request);
        Toolkit toolkit = new Toolkit();
        registerBuiltinTools(toolkit, request);
        agentRuntimeMcpMountService.mount(toolkit, request.getMcps());
        Path agentWorkspace = agentRuntimeWorkspaceService.resolveAgentWorkspace(request);
        Path skillRoot = agentRuntimeSkillMountService.materialize(agentWorkspace, request.getSkills());
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
