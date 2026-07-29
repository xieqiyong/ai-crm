package com.hz.crm.web.observability;

import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.observability.domain.AuditLogEntity;
import com.hz.crm.observability.dto.AuditLogQuery;
import com.hz.crm.observability.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability/audit-log")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:audit:view')")
    public ApiResult<PageData<AuditLogEntity>> page(
            @RequestBody(required = false) AuditLogQuery query, JwtPrincipal principal) {
        return ApiResult.ok(auditLogService.page(principal.getTenantId(), query));
    }
}
