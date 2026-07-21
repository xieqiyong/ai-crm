package com.hz.crm.agent.web;

import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import com.hz.crm.agent.runtime.dto.AgentMcpSaveRequest;
import com.hz.crm.agent.runtime.dto.AgentRunRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.agent.runtime.dto.AgentSaveRequest;
import com.hz.crm.agent.runtime.dto.AgentSkillSaveRequest;
import com.hz.crm.agent.runtime.service.AgentDefinitionService;
import com.hz.crm.agent.runtime.service.AgentRunService;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.api.PageQuery;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private AgentDefinitionService agentDefinitionService;

    @Autowired
    private AgentRunService agentRunService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<PageData<AgentEntity>> page(PageQuery query, Authentication authentication) {
        JwtPrincipal principal = current(authentication);
        return ApiResult.ok(agentDefinitionService.page(principal.getTenantId(), query));
    }

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<PageData<AgentEntity>> pagePost(
            @RequestBody(required = false) PageQuery query, Authentication authentication) {
        JwtPrincipal principal = current(authentication);
        return ApiResult.ok(agentDefinitionService.page(principal.getTenantId(), query));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<AgentEntity> save(@Valid @RequestBody AgentSaveRequest request, Authentication authentication) {
        JwtPrincipal principal = current(authentication);
        return ApiResult.ok(agentDefinitionService.saveAgent(principal.getTenantId(), request));
    }

    @PostMapping("/mcp/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<AgentMcpEntity> saveMcp(
            @Valid @RequestBody AgentMcpSaveRequest request, Authentication authentication) {
        JwtPrincipal principal = current(authentication);
        return ApiResult.ok(agentDefinitionService.saveMcp(principal.getTenantId(), request));
    }

    @PostMapping("/skill/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<AgentSkillEntity> saveSkill(
            @Valid @RequestBody AgentSkillSaveRequest request, Authentication authentication) {
        JwtPrincipal principal = current(authentication);
        return ApiResult.ok(agentDefinitionService.saveSkill(principal.getTenantId(), request));
    }

    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public SseEmitter runStream(@Valid @RequestBody AgentRunRequest request, Authentication authentication) {
        JwtPrincipal principal = current(authentication);
        SseEmitter emitter = new SseEmitter(0L);
        Disposable disposable = agentRunService
                .run(principal.getTenantId(), principal.getUserId(), request)
                .subscribe(
                        event -> sendEvent(emitter, event),
                        emitter::completeWithError,
                        emitter::complete);
        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(disposable::dispose);
        emitter.onError(error -> disposable.dispose());
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, AgentRuntimeEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.getType()).data(event));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private JwtPrincipal current(Authentication authentication) {
        return (JwtPrincipal) authentication.getPrincipal();
    }
}
