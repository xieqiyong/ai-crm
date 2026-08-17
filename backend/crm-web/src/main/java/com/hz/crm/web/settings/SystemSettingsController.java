package com.hz.crm.web.settings;

import com.hz.crm.application.system.SystemParameterApplicationService;
import com.hz.crm.application.system.dto.FollowupTaskSettingsResponse;
import com.hz.crm.application.system.dto.FollowupTaskSettingsSaveRequest;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.audit.AuditOperation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SystemSettingsController {

    @Autowired
    private SystemParameterApplicationService systemParameterApplicationService;

    @PostMapping("/followup-task/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:settings:view') or hasAuthority('crm:settings:manage')")
    public ApiResult<FollowupTaskSettingsResponse> followupTaskDetail(JwtPrincipal principal) {
        return ApiResult.ok(systemParameterApplicationService.followupTaskSettings(principal.getTenantId()));
    }

    @PostMapping("/followup-task/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:settings:manage')")
    @AuditOperation(
            module = "SETTINGS",
            action = "SAVE_FOLLOWUP_TASK",
            description = "保存跟进任务系统参数",
            targetType = "SYSTEM_PARAMETER")
    public ApiResult<FollowupTaskSettingsResponse> saveFollowupTask(
            @Valid @RequestBody FollowupTaskSettingsSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(systemParameterApplicationService.saveFollowupTaskSettings(
                principal.getTenantId(), principal.getUserId(), request));
    }
}
