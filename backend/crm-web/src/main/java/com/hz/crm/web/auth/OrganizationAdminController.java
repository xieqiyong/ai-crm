package com.hz.crm.web.auth;

import com.hz.crm.auth.dto.admin.AdminIdRequest;
import com.hz.crm.auth.dto.admin.AdminOverviewResponse;
import com.hz.crm.auth.dto.admin.DepartmentResponse;
import com.hz.crm.auth.dto.admin.DepartmentSaveRequest;
import com.hz.crm.auth.dto.admin.PermissionResponse;
import com.hz.crm.auth.dto.admin.PermissionSaveRequest;
import com.hz.crm.auth.dto.admin.PermissionStatusRequest;
import com.hz.crm.auth.dto.admin.RoleResponse;
import com.hz.crm.auth.dto.admin.RoleSaveRequest;
import com.hz.crm.auth.dto.admin.UserPasswordResetResponse;
import com.hz.crm.auth.dto.admin.UserResponse;
import com.hz.crm.auth.dto.admin.UserSaveRequest;
import com.hz.crm.auth.dto.admin.UserStatusRequest;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.auth.service.OrganizationAdminService;
import com.hz.crm.common.api.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/security")
public class OrganizationAdminController {

    @Autowired
    private OrganizationAdminService organizationAdminService;

    @PostMapping("/overview")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:view') or hasAuthority('crm:org:manage')")
    public ApiResult<AdminOverviewResponse> overviewPost(JwtPrincipal principal) {
        return ApiResult.ok(organizationAdminService.overview(principal.getTenantId()));
    }

    @PostMapping("/departments/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:manage')")
    public ApiResult<DepartmentResponse> saveDepartment(
            @RequestBody DepartmentSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(organizationAdminService.saveDepartment(principal.getTenantId(), request));
    }

    @PostMapping("/departments/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:manage')")
    public ApiResult<Void> deleteDepartment(@RequestBody AdminIdRequest request, JwtPrincipal principal) {
        organizationAdminService.deleteDepartment(principal.getTenantId(), request);
        return ApiResult.ok(null);
    }

    @PostMapping("/permissions/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:manage')")
    public ApiResult<PermissionResponse> savePermission(
            @RequestBody PermissionSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(organizationAdminService.savePermission(principal.getTenantId(), request));
    }

    @PostMapping("/permissions/status")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:manage')")
    public ApiResult<PermissionResponse> updatePermissionStatus(
            @RequestBody PermissionStatusRequest request, JwtPrincipal principal) {
        return ApiResult.ok(organizationAdminService.updatePermissionStatus(principal.getTenantId(), request));
    }

    @PostMapping("/permissions/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:manage')")
    public ApiResult<Void> deletePermission(@RequestBody AdminIdRequest request, JwtPrincipal principal) {
        organizationAdminService.deletePermission(principal.getTenantId(), request);
        return ApiResult.ok(null);
    }

    @PostMapping("/roles/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:manage')")
    public ApiResult<RoleResponse> saveRole(@RequestBody RoleSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(organizationAdminService.saveRole(principal.getTenantId(), request));
    }

    @PostMapping("/roles/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:manage')")
    public ApiResult<Void> deleteRole(@RequestBody AdminIdRequest request, JwtPrincipal principal) {
        organizationAdminService.deleteRole(principal.getTenantId(), request);
        return ApiResult.ok(null);
    }

    @PostMapping("/users/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:manage')")
    public ApiResult<UserResponse> saveUser(@RequestBody UserSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(organizationAdminService.saveUser(principal.getTenantId(), request));
    }

    @PostMapping("/users/status")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:manage')")
    public ApiResult<UserResponse> updateUserStatus(@RequestBody UserStatusRequest request, JwtPrincipal principal) {
        return ApiResult.ok(organizationAdminService.updateUserStatus(principal.getTenantId(), request));
    }

    @PostMapping("/users/reset-password")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:org:manage')")
    public ApiResult<UserPasswordResetResponse> resetUserPassword(
            @RequestBody AdminIdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(organizationAdminService.resetUserPassword(principal.getTenantId(), request));
    }
}
