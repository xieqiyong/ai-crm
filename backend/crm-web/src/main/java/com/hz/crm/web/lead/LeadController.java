package com.hz.crm.web.lead;

import com.hz.crm.application.lead.LeadApplicationService;
import com.hz.crm.application.lead.dto.LeadConvertRequest;
import com.hz.crm.application.lead.dto.LeadConvertResponse;
import com.hz.crm.application.lead.dto.LeadQuery;
import com.hz.crm.application.lead.dto.LeadResponse;
import com.hz.crm.application.lead.dto.LeadSaveRequest;
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
@RequestMapping("/api/lead")
public class LeadController {

    @Autowired
    private LeadApplicationService leadApplicationService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:lead:view')")
    public ApiResult<PageData<LeadResponse>> pagePost(
            @RequestBody(required = false) LeadQuery query, JwtPrincipal principal) {
        return ApiResult.ok(
                leadApplicationService.page(principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:lead:view')")
    public ApiResult<LeadResponse> detailPost(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(leadApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:lead:manage') "
            + "or (#request != null and #request.id == null and hasAuthority('crm:lead:create'))")
    public ApiResult<LeadResponse> save(@Valid @RequestBody LeadSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(leadApplicationService.save(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:lead:manage')")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        leadApplicationService.delete(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId());
        return ApiResult.ok(null);
    }

    @PostMapping("/convert-to-customer")
    @PreAuthorize("hasAuthority('*') or (hasAuthority('crm:lead:manage') "
            + "and (hasAuthority('crm:customer:manage') or hasAuthority('crm:customer:edit')))")
    public ApiResult<LeadConvertResponse> convertToCustomer(
            @Valid @RequestBody LeadConvertRequest request, JwtPrincipal principal) {
        return ApiResult.ok(leadApplicationService.convertToCustomer(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }
}
