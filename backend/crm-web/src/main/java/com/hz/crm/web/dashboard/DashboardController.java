package com.hz.crm.web.dashboard;

import com.hz.crm.application.dashboard.DashboardApplicationService;
import com.hz.crm.application.dashboard.dto.DashboardOverviewResponse;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.web.support.WebUserSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardApplicationService dashboardApplicationService;

    @Autowired
    private WebUserSupport webUserSupport;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:dashboard:view')")
    public ApiResult<DashboardOverviewResponse> overview(Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(
                dashboardApplicationService.overview(principal.getTenantId(), principal.getUserId(), principal.getDataScope()));
    }

    @PostMapping("/overview")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:dashboard:view')")
    public ApiResult<DashboardOverviewResponse> overviewPost(Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(
                dashboardApplicationService.overview(principal.getTenantId(), principal.getUserId(), principal.getDataScope()));
    }
}
