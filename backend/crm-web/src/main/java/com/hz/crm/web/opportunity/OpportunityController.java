package com.hz.crm.web.opportunity;

import com.hz.crm.application.opportunity.OpportunityApplicationService;
import com.hz.crm.application.opportunity.dto.OpportunityQuery;
import com.hz.crm.application.opportunity.dto.OpportunityResponse;
import com.hz.crm.application.opportunity.dto.OpportunitySaveRequest;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.web.support.IdRequest;
import com.hz.crm.web.support.WebUserSupport;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opportunity")
public class OpportunityController {

    @Autowired
    private OpportunityApplicationService opportunityApplicationService;

    @Autowired
    private WebUserSupport webUserSupport;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:opportunity:view')")
    public ApiResult<PageData<OpportunityResponse>> page(OpportunityQuery query, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(opportunityApplicationService.page(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:opportunity:view')")
    public ApiResult<PageData<OpportunityResponse>> pagePost(
            @RequestBody(required = false) OpportunityQuery query, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(opportunityApplicationService.page(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @GetMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:opportunity:view')")
    public ApiResult<OpportunityResponse> detail(@RequestParam Long id, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(opportunityApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), id));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:opportunity:view')")
    public ApiResult<OpportunityResponse> detailPost(@RequestBody IdRequest request, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(opportunityApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:opportunity:manage')")
    public ApiResult<OpportunityResponse> save(
            @Valid @RequestBody OpportunitySaveRequest request, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(
                opportunityApplicationService.save(principal.getTenantId(), principal.getUserId(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:opportunity:manage')")
    public ApiResult<Void> delete(@RequestBody IdRequest request, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        opportunityApplicationService.delete(principal.getTenantId(), request.getId());
        return ApiResult.ok(null);
    }
}
