package com.hz.crm.web.customer;

import com.hz.crm.application.customer.CustomerApplicationService;
import com.hz.crm.application.customer.dto.CustomerAssignRequest;
import com.hz.crm.application.customer.dto.CustomerQuery;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.application.customer.dto.CustomerSaveRequest;
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
@RequestMapping("/api/customer")
public class CustomerController {

    @Autowired
    private CustomerApplicationService customerApplicationService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:view')")
    public ApiResult<PageData<CustomerResponse>> pagePost(
            @RequestBody(required = false) CustomerQuery query, JwtPrincipal principal) {
        return ApiResult.ok(customerApplicationService.page(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:view')")
    public ApiResult<CustomerResponse> detailPost(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(customerApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:manage') or hasAuthority('crm:customer:edit')")
    @AuditOperation(
            module = "CUSTOMER",
            action = "SAVE",
            description = "保存客户",
            targetType = "CUSTOMER")
    public ApiResult<CustomerResponse> save(@Valid @RequestBody CustomerSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(customerApplicationService.save(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:manage')")
    @AuditOperation(
            module = "CUSTOMER",
            action = "DELETE",
            description = "删除客户",
            targetType = "CUSTOMER")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        customerApplicationService.delete(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId());
        return ApiResult.ok(null);
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:customer:assign')")
    @AuditOperation(
            module = "CUSTOMER",
            action = "ASSIGN",
            description = "分配客户负责人",
            targetType = "CUSTOMER")
    public ApiResult<CustomerResponse> assign(
            @Valid @RequestBody CustomerAssignRequest request, JwtPrincipal principal) {
        return ApiResult.ok(customerApplicationService.assign(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }
}
