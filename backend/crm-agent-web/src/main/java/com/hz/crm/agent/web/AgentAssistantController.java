package com.hz.crm.agent.web;

import com.hz.crm.agent.web.dto.AgentAssistantAgentResponse;
import com.hz.crm.agent.web.dto.AgentAssistantConversationActionRequest;
import com.hz.crm.agent.web.dto.AgentAssistantConversationListRequest;
import com.hz.crm.agent.web.dto.AgentAssistantConversationResponse;
import com.hz.crm.agent.web.dto.AgentAssistantMessagesRequest;
import com.hz.crm.agent.web.dto.AgentAssistantMessageResponse;
import com.hz.crm.agent.web.dto.AgentAssistantRunRequest;
import com.hz.crm.agent.web.dto.AgentAssistantRunStopRequest;
import com.hz.crm.agent.web.service.AgentAssistantWorkbenchService;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent-assistant")
public class AgentAssistantController {

    @Autowired
    private AgentAssistantWorkbenchService agentAssistantWorkbenchService;

    @PostMapping("/agents")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:assistant:use')")
    public ApiResult<List<AgentAssistantAgentResponse>> agents(JwtPrincipal principal) {
        return ApiResult.ok(agentAssistantWorkbenchService.agents(principal.getTenantId(), principal.getUserId()));
    }

    @PostMapping("/conversations")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:assistant:use')")
    public ApiResult<List<AgentAssistantConversationResponse>> conversations(
            @RequestBody(required = false) AgentAssistantConversationListRequest request,
            JwtPrincipal principal) {
        return ApiResult.ok(agentAssistantWorkbenchService.conversations(
                principal.getTenantId(), principal.getUserId(), request));
    }

    @PostMapping("/messages")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:assistant:use')")
    public ApiResult<List<AgentAssistantMessageResponse>> messages(
            @Valid @RequestBody AgentAssistantMessagesRequest request,
            JwtPrincipal principal) {
        return ApiResult.ok(agentAssistantWorkbenchService.messages(
                principal.getTenantId(), principal.getUserId(), request));
    }

    @PostMapping("/conversation/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:assistant:use')")
    public ApiResult<Boolean> deleteConversation(
            @Valid @RequestBody AgentAssistantConversationActionRequest request,
            JwtPrincipal principal) {
        agentAssistantWorkbenchService.deleteConversation(principal.getTenantId(), principal.getUserId(), request);
        return ApiResult.ok(Boolean.TRUE);
    }

    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:assistant:use')")
    public SseEmitter runStream(
            @Valid @RequestBody AgentAssistantRunRequest request,
            JwtPrincipal principal) {
        return agentAssistantWorkbenchService.runStream(principal.getTenantId(), principal.getUserId(), request);
    }

    @PostMapping("/run/stop")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:assistant:use')")
    public ApiResult<Boolean> stopRun(
            @Valid @RequestBody AgentAssistantRunStopRequest request,
            JwtPrincipal principal) {
        return ApiResult.ok(agentAssistantWorkbenchService.stopRun(
                principal.getTenantId(), principal.getUserId(), request));
    }
}
