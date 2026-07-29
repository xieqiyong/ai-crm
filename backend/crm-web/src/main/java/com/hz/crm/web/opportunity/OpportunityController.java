package com.hz.crm.web.opportunity;

import com.hz.crm.application.opportunity.OpportunityApplicationService;
import com.hz.crm.application.opportunity.dto.OpportunityQuery;
import com.hz.crm.application.opportunity.dto.OpportunityResponse;
import com.hz.crm.application.opportunity.dto.OpportunitySaveRequest;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.audit.AuditOperation;
import com.hz.crm.web.support.IdRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opportunity")
public class OpportunityController {

    @Autowired
    private OpportunityApplicationService opportunityApplicationService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:opportunity:view')")
    public ApiResult<PageData<OpportunityResponse>> pagePost(
            @RequestBody(required = false) OpportunityQuery query, JwtPrincipal principal) {
        return ApiResult.ok(opportunityApplicationService.page(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:opportunity:view')")
    public ApiResult<OpportunityResponse> detailPost(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(opportunityApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:opportunity:manage') "
            + "or (#request != null and #request.id == null and hasAuthority('crm:opportunity:create'))")
    @AuditOperation(
            module = "OPPORTUNITY",
            action = "SAVE",
            description = "保存商机",
            targetType = "OPPORTUNITY")
    public ApiResult<OpportunityResponse> save(
            @Valid @RequestBody OpportunitySaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(
                opportunityApplicationService.save(
                        principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:opportunity:manage')")
    @AuditOperation(
            module = "OPPORTUNITY",
            action = "DELETE",
            description = "删除商机",
            targetType = "OPPORTUNITY")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        opportunityApplicationService.delete(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId());
        return ApiResult.ok(null);
    }
}
