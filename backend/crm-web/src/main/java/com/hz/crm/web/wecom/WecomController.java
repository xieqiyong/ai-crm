package com.hz.crm.web.wecom;

import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.audit.AuditOperation;
import com.hz.crm.wecom.dto.WecomBindingResponse;
import com.hz.crm.wecom.dto.WecomBindingSaveRequest;
import com.hz.crm.wecom.dto.WecomConfigIdRequest;
import com.hz.crm.wecom.dto.WecomConfigResponse;
import com.hz.crm.wecom.dto.WecomConfigSaveRequest;
import com.hz.crm.wecom.dto.WecomSyncTaskResponse;
import com.hz.crm.wecom.service.WecomManageService;
import com.hz.crm.web.support.IdRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wecom")
public class WecomController {

    @Autowired
    private WecomManageService manageService;

    @PostMapping("/config/detail")
    @PreAuthorize(
            "hasAuthority('*') or hasAuthority('crm:wecom:view') or hasAuthority('crm:channel:view')")
    public ApiResult<WecomConfigResponse> configDetail(JwtPrincipal principal) {
        return ApiResult.ok(manageService.detail(principal.getTenantId()));
    }

    @PostMapping("/config/save")
    @PreAuthorize(
            "hasAuthority('*') or hasAuthority('crm:wecom:manage') or hasAuthority('crm:channel:manage')")
    @AuditOperation(
            module = "WECOM",
            action = "CONFIG_SAVE",
            description = "保存企业微信同步配置",
            targetType = "WECOM_CONFIG",
            recordParameters = false)
    public ApiResult<WecomConfigResponse> configSave(
            @RequestBody WecomConfigSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(manageService.save(principal.getTenantId(), request));
    }

    @PostMapping("/binding/list")
    @PreAuthorize(
            "hasAuthority('*') or hasAuthority('crm:wecom:view') or hasAuthority('crm:channel:view')")
    public ApiResult<List<WecomBindingResponse>> bindingList(
            @RequestBody WecomConfigIdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(manageService.listBindings(
                principal.getTenantId(), request.getConfigId()));
    }

    @PostMapping("/binding/save")
    @PreAuthorize(
            "hasAuthority('*') or hasAuthority('crm:wecom:manage') or hasAuthority('crm:channel:manage')")
    @AuditOperation(
            module = "WECOM",
            action = "BINDING_SAVE",
            description = "保存企业微信员工映射",
            targetType = "WECOM_BINDING")
    public ApiResult<List<WecomBindingResponse>> bindingSave(
            @RequestBody WecomBindingSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(manageService.saveBindings(principal.getTenantId(), request));
    }

    @PostMapping("/sync/latest")
    @PreAuthorize(
            "hasAuthority('*') or hasAuthority('crm:wecom:view') or hasAuthority('crm:channel:view')")
    public ApiResult<WecomSyncTaskResponse> syncLatest(
            @RequestBody WecomConfigIdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(manageService.latestTask(
                principal.getTenantId(), request.getConfigId()));
    }

    @PostMapping("/sync/detail")
    @PreAuthorize(
            "hasAuthority('*') or hasAuthority('crm:wecom:view') or hasAuthority('crm:channel:view')")
    public ApiResult<WecomSyncTaskResponse> syncDetail(
            @RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(manageService.taskDetail(principal.getTenantId(), request.getId()));
    }
}
