package com.hz.crm.web.task;

import com.hz.crm.application.task.SalesTaskApplicationService;
import com.hz.crm.application.task.dto.SalesTaskAssignRequest;
import com.hz.crm.application.task.dto.SalesTaskQuery;
import com.hz.crm.application.task.dto.SalesTaskResponse;
import com.hz.crm.application.task.dto.SalesTaskSaveRequest;
import com.hz.crm.application.task.dto.SalesTaskStatusRequest;
import com.hz.crm.application.task.dto.SalesTaskTargetOptionQuery;
import com.hz.crm.application.task.dto.SalesTaskTargetOptionResponse;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.audit.AuditOperation;
import com.hz.crm.web.support.IdRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task")
public class SalesTaskController {

    @Autowired
    private SalesTaskApplicationService salesTaskApplicationService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:task:view')")
    public ApiResult<PageData<SalesTaskResponse>> page(
            @RequestBody(required = false) SalesTaskQuery query, JwtPrincipal principal) {
        return ApiResult.ok(salesTaskApplicationService.page(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:task:view')")
    public ApiResult<SalesTaskResponse> detail(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(salesTaskApplicationService.detail(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/target-options")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:task:view') "
            + "or hasAuthority('crm:task:manage') or hasAuthority('crm:task:create')")
    public ApiResult<List<SalesTaskTargetOptionResponse>> targetOptions(
            @RequestBody(required = false) SalesTaskTargetOptionQuery query, JwtPrincipal principal) {
        return ApiResult.ok(salesTaskApplicationService.targetOptions(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), query));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:task:manage') "
            + "or (#request != null and #request.id == null and hasAuthority('crm:task:create'))")
    @AuditOperation(
            module = "TASK",
            action = "SAVE",
            description = "保存销售任务",
            targetType = "TASK")
    public ApiResult<SalesTaskResponse> save(@Valid @RequestBody SalesTaskSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(salesTaskApplicationService.save(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:task:manage') or hasAuthority('crm:task:create')")
    @AuditOperation(
            module = "TASK",
            action = "START",
            description = "开始销售任务",
            targetType = "TASK")
    public ApiResult<SalesTaskResponse> start(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(salesTaskApplicationService.start(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:task:manage') or hasAuthority('crm:task:create')")
    @AuditOperation(
            module = "TASK",
            action = "COMPLETE",
            description = "完成销售任务",
            targetType = "TASK")
    public ApiResult<SalesTaskResponse> complete(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(salesTaskApplicationService.complete(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId()));
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:task:manage')")
    @AuditOperation(
            module = "TASK",
            action = "CANCEL",
            description = "取消销售任务",
            targetType = "TASK")
    public ApiResult<SalesTaskResponse> cancel(@RequestBody SalesTaskStatusRequest request, JwtPrincipal principal) {
        return ApiResult.ok(salesTaskApplicationService.cancel(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:task:assign') or hasAuthority('crm:task:manage')")
    @AuditOperation(
            module = "TASK",
            action = "ASSIGN",
            description = "分配销售任务",
            targetType = "TASK")
    public ApiResult<SalesTaskResponse> assign(
            @Valid @RequestBody SalesTaskAssignRequest request, JwtPrincipal principal) {
        return ApiResult.ok(salesTaskApplicationService.assign(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:task:manage')")
    @AuditOperation(
            module = "TASK",
            action = "DELETE",
            description = "删除销售任务",
            targetType = "TASK")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        salesTaskApplicationService.delete(
                principal.getTenantId(), principal.getUserId(), principal.getDataScope(), request.getId());
        return ApiResult.ok(null);
    }
}
