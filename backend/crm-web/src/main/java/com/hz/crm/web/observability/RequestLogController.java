package com.hz.crm.web.observability;

import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.api.PageQuery;
import com.hz.crm.observability.domain.RequestLogEntity;
import com.hz.crm.observability.service.RequestLogService;
import com.hz.crm.web.support.WebUserSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability/request-log")
public class RequestLogController {

    @Autowired
    private RequestLogService requestLogService;

    @Autowired
    private WebUserSupport webUserSupport;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:observability:view')")
    public ApiResult<PageData<RequestLogEntity>> page(PageQuery query, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(requestLogService.page(principal.getTenantId(), query));
    }

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:observability:view')")
    public ApiResult<PageData<RequestLogEntity>> pagePost(
            @RequestBody(required = false) PageQuery query, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(requestLogService.page(principal.getTenantId(), query));
    }
}
