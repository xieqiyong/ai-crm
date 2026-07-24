package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.DataScope;
import com.hz.crm.auth.domain.SysDepartmentEntity;
import com.hz.crm.auth.domain.SysPermissionEntity;
import com.hz.crm.auth.domain.SysRoleEntity;
import com.hz.crm.auth.domain.SysRolePermissionEntity;
import com.hz.crm.auth.domain.SysTenantEntity;
import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.domain.SysUserRoleEntity;
import com.hz.crm.auth.dto.InstallStatusResponse;
import com.hz.crm.auth.dto.LoginRequest;
import com.hz.crm.auth.dto.LoginResponse;
import com.hz.crm.auth.dto.SetupSuperAdminRequest;
import com.hz.crm.auth.repository.SysRolePermissionRepository;
import com.hz.crm.auth.repository.SysRoleRepository;
import com.hz.crm.auth.repository.SysUserRepository;
import com.hz.crm.auth.repository.SysUserRoleRepository;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstallationService {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysRolePermissionRepository rolePermissionRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private AuthApplicationService authApplicationService;

    @Autowired
    private PermissionSeedService permissionSeedService;

    @Autowired
    private DepartmentSeedService departmentSeedService;

    @Autowired
    private TenantService tenantService;

    @Transactional(readOnly = true)
    public InstallStatusResponse status() {
        InstallStatusResponse response = new InstallStatusResponse();
        response.setInstalled(hasSuperAdmin());
        return response;
    }

    @Transactional
    public LoginResponse setup(SetupSuperAdminRequest request) {
        if (hasSuperAdmin()) {
            throw new BusinessException("INSTALL_001", "系统已完成初始化");
        }
        if (request == null || request.getUsername() == null || request.getUsername().trim().length() == 0) {
            throw new BusinessException("INSTALL_003", "超管用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new BusinessException("INSTALL_002", "超管密码长度不能少于8位");
        }
        SysTenantEntity tenant = tenantService.createInitialTenant(request.getTenantName());
        Long tenantId = tenant.getId();
        SysDepartmentEntity rootDepartment =
                departmentSeedService.ensureTenantRootDepartment(tenantId, tenant.getName());
        List<SysPermissionEntity> permissions = permissionSeedService.seedBasePermissions(tenantId);
        SysRoleEntity role = new SysRoleEntity();
        role.setId(snowflakeIdGenerator.nextId());
        role.setTenantId(tenantId);
        role.setCode(SUPER_ADMIN_ROLE);
        role.setName("超级管理员");
        role.setDataScope(DataScope.ALL);
        roleRepository.save(role);
        for (SysPermissionEntity permission : permissions) {
            SysRolePermissionEntity rolePermission = new SysRolePermissionEntity();
            rolePermission.setId(snowflakeIdGenerator.nextId());
            rolePermission.setTenantId(tenantId);
            rolePermission.setRoleId(role.getId());
            rolePermission.setPermissionId(permission.getId());
            rolePermissionRepository.save(rolePermission);
        }
        SysUserEntity user = new SysUserEntity();
        user.setId(snowflakeIdGenerator.nextId());
        user.setTenantId(tenantId);
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(resolveDisplayName(request));
        user.setDepartmentId(rootDepartment.getId());
        userRepository.save(user);
        SysUserRoleEntity userRole = new SysUserRoleEntity();
        userRole.setId(snowflakeIdGenerator.nextId());
        userRole.setTenantId(tenantId);
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRoleRepository.save(userRole);
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setTenantId(tenantId);
        loginRequest.setUsername(user.getUsername());
        loginRequest.setPassword(request.getPassword());
        return authApplicationService.login(loginRequest);
    }

    private boolean hasSuperAdmin() {
        List<SysRoleEntity> roles = roleRepository.findByCodeAndDeletedFalse(SUPER_ADMIN_ROLE);
        for (SysRoleEntity role : roles) {
            if (userRoleRepository.existsByRoleIdAndTenantIdAndDeletedFalse(role.getId(), role.getTenantId())) {
                return true;
            }
        }
        return false;
    }

    private String resolveDisplayName(SetupSuperAdminRequest request) {
        if (request.getDisplayName() == null || request.getDisplayName().trim().length() == 0) {
            return request.getUsername().trim();
        }
        return request.getDisplayName().trim();
    }

}
