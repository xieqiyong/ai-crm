package com.hz.crm.agent.web;

import com.hz.crm.agent.runtime.dto.ModelConfigDebugRequest;
import com.hz.crm.agent.runtime.dto.ModelConfigDebugResponse;
import com.hz.crm.agent.runtime.dto.ModelConfigIdRequest;
import com.hz.crm.agent.runtime.dto.ModelConfigRequest;
import com.hz.crm.agent.runtime.dto.ModelConfigResponse;
import com.hz.crm.agent.runtime.dto.ModelConfigStatusResponse;
import com.hz.crm.agent.runtime.service.ModelConfigService;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.audit.AuditOperation;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/model-configs")
public class ModelConfigController {

    @Autowired
    private ModelConfigService modelConfigService;

    @PostMapping("/list")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:model:view') "
            + "or hasAuthority('crm:model:manage') or hasAuthority('crm:agent:view') "
            + "or hasAuthority('crm:agent:manage')")
    public ApiResult<List<ModelConfigResponse>> listPost(JwtPrincipal principal) {
        return ApiResult.ok(modelConfigService.list(principal.getTenantId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:model:manage')")
    @AuditOperation(
            module = "MODEL",
            action = "CONFIG_SAVE",
            description = "保存大模型配置",
            targetType = "MODEL_CONFIG")
    public ApiResult<ModelConfigResponse> save(@RequestBody ModelConfigRequest request, JwtPrincipal principal) {
        return ApiResult.ok(modelConfigService.save(principal.getTenantId(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:model:manage')")
    @AuditOperation(
            module = "MODEL",
            action = "CONFIG_DELETE",
            description = "删除大模型配置",
            targetType = "MODEL_CONFIG")
    public ApiResult<Void> delete(@RequestBody ModelConfigIdRequest request, JwtPrincipal principal) {
        modelConfigService.delete(principal.getTenantId(), request);
        return ApiResult.ok(null);
    }

    @PostMapping("/default")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:model:manage')")
    @AuditOperation(
            module = "MODEL",
            action = "CONFIG_DEFAULT",
            description = "设置默认大模型",
            targetType = "MODEL_CONFIG")
    public ApiResult<ModelConfigResponse> setDefault(@RequestBody ModelConfigIdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(modelConfigService.setDefault(principal.getTenantId(), request));
    }

    @PostMapping("/status")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:model:view') or hasAuthority('crm:model:manage')")
    public ApiResult<ModelConfigStatusResponse> status(@RequestBody ModelConfigIdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(modelConfigService.status(principal.getTenantId(), request));
    }

    @PostMapping("/debug")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:model:manage')")
    @AuditOperation(
            module = "MODEL",
            action = "CONFIG_DEBUG",
            description = "调试大模型配置",
            targetType = "MODEL_CONFIG",
            recordParameters = false)
    public ApiResult<ModelConfigDebugResponse> debug(
            @RequestBody ModelConfigDebugRequest request, JwtPrincipal principal) {
        return ApiResult.ok(modelConfigService.debug(principal.getTenantId(), request));
    }
}
