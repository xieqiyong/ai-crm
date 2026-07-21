package com.hz.crm.web.customer;

import com.hz.crm.application.customer.CustomerApplicationService;
import com.hz.crm.application.customer.dto.CustomerQuery;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.application.customer.dto.CustomerSaveRequest;
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
@RequestMapping("/api/customer")
public class CustomerController {

    @Autowired
    private CustomerApplicationService customerApplicationService;

    @Autowired
    private WebUserSupport webUserSupport;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:view')")
    public ApiResult<PageData<CustomerResponse>> page(CustomerQuery query, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(customerApplicationService.page(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:view')")
    public ApiResult<PageData<CustomerResponse>> pagePost(
            @RequestBody(required = false) CustomerQuery query, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(customerApplicationService.page(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @GetMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:view')")
    public ApiResult<CustomerResponse> detail(@RequestParam Long id, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(customerApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), id));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:view')")
    public ApiResult<CustomerResponse> detailPost(@RequestBody IdRequest request, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(customerApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:manage')")
    public ApiResult<CustomerResponse> save(
            @Valid @RequestBody CustomerSaveRequest request, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        return ApiResult.ok(customerApplicationService.save(principal.getTenantId(), principal.getUserId(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:manage')")
    public ApiResult<Void> delete(@RequestBody IdRequest request, Authentication authentication) {
        JwtPrincipal principal = webUserSupport.current(authentication);
        customerApplicationService.delete(principal.getTenantId(), request.getId());
        return ApiResult.ok(null);
    }
}
