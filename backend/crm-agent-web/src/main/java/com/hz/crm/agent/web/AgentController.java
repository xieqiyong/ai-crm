package com.hz.crm.agent.web;

import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.exception.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:view') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> page() {
        throw migrated();
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:view') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> detail() {
        throw migrated();
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> save() {
        throw migrated();
    }

    @PostMapping("/mcp/list")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:view') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> mcps() {
        throw migrated();
    }

    @PostMapping("/mcp/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> saveMcp() {
        throw migrated();
    }

    @PostMapping("/mcp/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> deleteMcp() {
        throw migrated();
    }

    @PostMapping("/skill/list")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:view') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> skills() {
        throw migrated();
    }

    @PostMapping("/skill/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> saveSkill() {
        throw migrated();
    }

    @PostMapping("/skill/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> deleteSkill() {
        throw migrated();
    }

    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public SseEmitter runStream() {
        throw migrated();
    }

    @PostMapping("/token/today")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<Object> tokenToday() {
        throw migrated();
    }

    @PostMapping("/token/quota/overview")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> tokenQuotaOverview() {
        throw migrated();
    }

    @PostMapping("/token/quota/assign")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> assignTokenQuota() {
        throw migrated();
    }

    @PostMapping("/token/quota/clear")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:agent:manage')")
    public ApiResult<Object> clearTokenQuota() {
        throw migrated();
    }

    private BusinessException migrated() {
        return new BusinessException("AI_RUNTIME_MIGRATED", "AI智能体配置和用量已迁移到Python Runtime，请通过Gateway访问");
    }
}
