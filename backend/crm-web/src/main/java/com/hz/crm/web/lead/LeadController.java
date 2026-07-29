package com.hz.crm.web.lead;

import com.hz.crm.application.lead.LeadApplicationService;
import com.hz.crm.application.lead.dto.LeadAssignRequest;
import com.hz.crm.application.lead.dto.LeadConvertRequest;
import com.hz.crm.application.lead.dto.LeadConvertResponse;
import com.hz.crm.application.lead.dto.LeadImportResult;
import com.hz.crm.application.lead.dto.LeadImportRow;
import com.hz.crm.application.lead.dto.LeadQuery;
import com.hz.crm.application.lead.dto.LeadResponse;
import com.hz.crm.application.lead.dto.LeadSaveRequest;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.audit.AuditOperation;
import com.hz.crm.web.support.IdRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/lead")
public class LeadController {

    @Autowired
    private LeadApplicationService leadApplicationService;

    @Autowired
    private LeadExcelImportParser leadExcelImportParser;

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
    @AuditOperation(
            module = "LEAD",
            action = "SAVE",
            description = "保存线索",
            targetType = "LEAD")
    public ApiResult<LeadResponse> save(@Valid @RequestBody LeadSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(leadApplicationService.save(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:lead:manage')")
    @AuditOperation(
            module = "LEAD",
            action = "DELETE",
            description = "删除线索",
            targetType = "LEAD")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        leadApplicationService.delete(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId());
        return ApiResult.ok(null);
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:lead:assign')")
    @AuditOperation(
            module = "LEAD",
            action = "ASSIGN",
            description = "分配线索负责人",
            targetType = "LEAD")
    public ApiResult<LeadResponse> assign(
            @Valid @RequestBody LeadAssignRequest request, JwtPrincipal principal) {
        return ApiResult.ok(leadApplicationService.assign(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:lead:import') or hasAuthority('crm:lead:manage')")
    @AuditOperation(
            module = "LEAD",
            action = "IMPORT",
            description = "导入线索",
            targetType = "LEAD",
            recordParameters = false)
    public ApiResult<LeadImportResult> importExcel(
            @RequestParam("file") MultipartFile file, JwtPrincipal principal) {
        List<LeadImportRow> rows = leadExcelImportParser.parse(file);
        return ApiResult.ok(leadApplicationService.importRows(
                principal.getTenantId(), principal.getUserId(), rows));
    }

    @PostMapping("/convert-to-customer")
    @PreAuthorize("hasAuthority('*') or (hasAuthority('crm:lead:manage') "
            + "and (hasAuthority('crm:customer:manage') or hasAuthority('crm:customer:edit')))")
    @AuditOperation(
            module = "LEAD",
            action = "CONVERT",
            description = "线索转为客户",
            targetType = "LEAD",
            targetIdField = "leadId")
    public ApiResult<LeadConvertResponse> convertToCustomer(
            @Valid @RequestBody LeadConvertRequest request, JwtPrincipal principal) {
        return ApiResult.ok(leadApplicationService.convertToCustomer(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }
}
