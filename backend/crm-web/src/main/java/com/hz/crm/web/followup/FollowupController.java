package com.hz.crm.web.followup;

import com.hz.crm.application.followup.FollowupApplicationService;
import com.hz.crm.application.followup.dto.FollowupQuery;
import com.hz.crm.application.followup.dto.FollowupResponse;
import com.hz.crm.application.followup.dto.FollowupSaveRequest;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.web.support.IdRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/followup")
public class FollowupController {

    @Autowired
    private FollowupApplicationService followupApplicationService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:followup:view')")
    public ApiResult<PageData<FollowupResponse>> page(
            @RequestBody(required = false) FollowupQuery query, JwtPrincipal principal) {
        return ApiResult.ok(followupApplicationService.page(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:followup:view')")
    public ApiResult<FollowupResponse> detail(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(followupApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:followup:manage') "
            + "or (#request != null and #request.id == null and hasAuthority('crm:followup:create'))")
    public ApiResult<FollowupResponse> save(@Valid @RequestBody FollowupSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(followupApplicationService.save(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:followup:manage')")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        followupApplicationService.delete(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId());
        return ApiResult.ok(null);
    }
}
