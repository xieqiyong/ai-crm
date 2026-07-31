package com.hz.crm.agent.web;

import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import com.hz.crm.agent.runtime.dto.AgentConfigIdRequest;
import com.hz.crm.agent.runtime.dto.AgentIdRequest;
import com.hz.crm.agent.runtime.dto.AgentMcpSaveRequest;
import com.hz.crm.agent.runtime.dto.AgentRunRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.agent.runtime.dto.AgentSaveRequest;
import com.hz.crm.agent.runtime.dto.AgentSkillSaveRequest;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaAssignRequest;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaClearRequest;
import com.hz.crm.agent.runtime.dto.AgentTokenQuotaOverviewResponse;
import com.hz.crm.agent.runtime.dto.AgentTokenUsageTodayResponse;
import com.hz.crm.agent.runtime.service.AgentDefinitionService;
import com.hz.crm.agent.runtime.service.AgentRunService;
import com.hz.crm.agent.runtime.service.AgentTokenQuotaService;
import com.hz.crm.agent.web.service.AgentTokenQuotaManageService;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.api.PageQuery;
import com.hz.crm.common.audit.AuditOperation;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Autowired
    private AgentTokenQuotaService agentTokenQuotaService;

    @Autowired
    private AgentTokenQuotaManageService agentTokenQuotaManageService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:view') or hasAuthority('crm:agent:manage')")
    public ApiResult<PageData<AgentEntity>> page(
            @RequestBody(required = false) PageQuery query, JwtPrincipal principal) {
        return ApiResult.ok(agentDefinitionService.page(principal.getTenantId(), query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:view') or hasAuthority('crm:agent:manage')")
    public ApiResult<AgentEntity> detail(
            @Valid @RequestBody AgentConfigIdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(agentDefinitionService.detail(principal.getTenantId(), request.getId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    @AuditOperation(
            module = "AGENT",
            action = "CONFIG_SAVE",
            description = "保存智能体配置",
            targetType = "AGENT")
    public ApiResult<AgentEntity> save(@Valid @RequestBody AgentSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(agentDefinitionService.saveAgent(principal.getTenantId(), request));
    }

    @PostMapping("/mcp/list")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:view') or hasAuthority('crm:agent:manage')")
    public ApiResult<List<AgentMcpEntity>> mcps(
            @Valid @RequestBody AgentIdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(agentDefinitionService.mcps(principal.getTenantId(), request.getAgentId()));
    }

    @PostMapping("/mcp/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    @AuditOperation(
            module = "AGENT",
            action = "MCP_SAVE",
            description = "保存智能体MCP配置",
            targetType = "AGENT_MCP")
    public ApiResult<AgentMcpEntity> saveMcp(
            @Valid @RequestBody AgentMcpSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(agentDefinitionService.saveMcp(principal.getTenantId(), request));
    }

    @PostMapping("/mcp/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    @AuditOperation(
            module = "AGENT",
            action = "MCP_DELETE",
            description = "删除智能体MCP配置",
            targetType = "AGENT_MCP")
    public ApiResult<Void> deleteMcp(
            @Valid @RequestBody AgentConfigIdRequest request, JwtPrincipal principal) {
        agentDefinitionService.deleteMcp(principal.getTenantId(), request.getId());
        return ApiResult.<Void>ok(null);
    }

    @PostMapping("/skill/list")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:view') or hasAuthority('crm:agent:manage')")
    public ApiResult<List<AgentSkillEntity>> skills(
            @Valid @RequestBody AgentIdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(agentDefinitionService.skills(principal.getTenantId(), request.getAgentId()));
    }

    @PostMapping("/skill/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    @AuditOperation(
            module = "AGENT",
            action = "SKILL_SAVE",
            description = "保存智能体技能配置",
            targetType = "AGENT_SKILL")
    public ApiResult<AgentSkillEntity> saveSkill(
            @Valid @RequestBody AgentSkillSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(agentDefinitionService.saveSkill(principal.getTenantId(), request));
    }

    @PostMapping("/skill/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    @AuditOperation(
            module = "AGENT",
            action = "SKILL_DELETE",
            description = "删除智能体技能配置",
            targetType = "AGENT_SKILL")
    public ApiResult<Void> deleteSkill(
            @Valid @RequestBody AgentConfigIdRequest request, JwtPrincipal principal) {
        agentDefinitionService.deleteSkill(principal.getTenantId(), request.getId());
        return ApiResult.<Void>ok(null);
    }

    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public SseEmitter runStream(@Valid @RequestBody AgentRunRequest request, JwtPrincipal principal) {
        fillRuntimeAccessContext(request, principal);
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

    private void fillRuntimeAccessContext(
            AgentRunRequest request, JwtPrincipal principal) {
        Map<String, Object> context = request.getContext();
        if (context == null) {
            context = new HashMap<String, Object>();
            request.setContext(context);
        }
        context.put("dataScope", principal.getDataScope());
        context.put(
                "permissions",
                principal.getPermissions() == null
                        ? new ArrayList<String>()
                        : new ArrayList<String>(principal.getPermissions()));
    }

    @PostMapping("/token/today")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<AgentTokenUsageTodayResponse> tokenToday(JwtPrincipal principal) {
        return ApiResult.ok(agentTokenQuotaService.today(principal.getTenantId(), principal.getUserId()));
    }

    @PostMapping("/token/quota/overview")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<AgentTokenQuotaOverviewResponse> tokenQuotaOverview(JwtPrincipal principal) {
        return ApiResult.ok(agentTokenQuotaManageService.overview(principal.getTenantId()));
    }

    @PostMapping("/token/quota/assign")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    @AuditOperation(
            module = "AGENT",
            action = "TOKEN_QUOTA_ASSIGN",
            description = "分配智能体用量额度",
            targetType = "TOKEN_QUOTA")
    public ApiResult<AgentTokenQuotaOverviewResponse> assignTokenQuota(
            @RequestBody AgentTokenQuotaAssignRequest request, JwtPrincipal principal) {
        return ApiResult.ok(agentTokenQuotaManageService.assign(principal.getTenantId(), request));
    }

    @PostMapping("/token/quota/clear")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    @AuditOperation(
            module = "AGENT",
            action = "TOKEN_QUOTA_CLEAR",
            description = "清除智能体用量额度",
            targetType = "TOKEN_QUOTA")
    public ApiResult<AgentTokenQuotaOverviewResponse> clearTokenQuota(
            @RequestBody AgentTokenQuotaClearRequest request, JwtPrincipal principal) {
        return ApiResult.ok(agentTokenQuotaManageService.clear(principal.getTenantId(), request));
    }

    private void sendEvent(SseEmitter emitter, AgentRuntimeEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.getType()).data(event));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
