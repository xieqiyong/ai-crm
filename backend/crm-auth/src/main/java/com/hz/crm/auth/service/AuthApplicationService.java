package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.DataScope;
import com.hz.crm.auth.domain.PermissionType;
import com.hz.crm.auth.domain.SysPermissionEntity;
import com.hz.crm.auth.domain.SysRoleEntity;
import com.hz.crm.auth.domain.SysRolePermissionEntity;
import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.domain.SysUserRoleEntity;
import com.hz.crm.auth.dto.LoginRequest;
import com.hz.crm.auth.dto.LoginResponse;
import com.hz.crm.auth.dto.PermissionProfile;
import com.hz.crm.auth.repository.SysPermissionRepository;
import com.hz.crm.auth.repository.SysRolePermissionRepository;
import com.hz.crm.auth.repository.SysRoleRepository;
import com.hz.crm.auth.repository.SysUserRepository;
import com.hz.crm.auth.repository.SysUserRoleRepository;
import com.hz.crm.auth.security.JwtService;
import com.hz.crm.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService {

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysRolePermissionRepository rolePermissionRepository;

    @Autowired
    private SysPermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String tenantId = normalizeTenant(request.getTenantId());
        SysUserEntity user = userRepository
                .findByUsernameAndTenantIdAndDeletedFalse(request.getUsername(), tenantId)
                .orElseThrow(() -> new BusinessException("AUTH_001", "账号或密码不正确"));
        if (!user.isEnabled()) {
            throw new BusinessException("AUTH_002", "账号已停用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("AUTH_001", "账号或密码不正确");
        }
        PermissionProfile profile = loadPermissions(user);
        LoginResponse response = new LoginResponse();
        response.setToken(jwtService.createToken(user, profile));
        response.setTenantId(user.getTenantId());
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setPermissions(new ArrayList<String>(profile.getPermissions()));
        response.setMenuPermissions(new ArrayList<String>(profile.getMenuPermissions()));
        response.setDataPermissions(new ArrayList<String>(profile.getDataPermissions()));
        response.setDataScope(profile.getDataScope().name());
        return response;
    }

    public PermissionProfile loadPermissions(SysUserEntity user) {
        List<SysUserRoleEntity> userRoles =
                userRoleRepository.findByUserIdAndTenantIdAndDeletedFalse(user.getId(), user.getTenantId());
        List<Long> roleIds = new ArrayList<Long>();
        for (SysUserRoleEntity userRole : userRoles) {
            roleIds.add(userRole.getRoleId());
        }
        if (roleIds.isEmpty()) {
            return new PermissionProfile();
        }
        List<SysRoleEntity> roles = roleRepository.findByIdInAndTenantIdAndEnabledTrueAndDeletedFalse(
                roleIds, user.getTenantId());
        List<Long> enabledRoleIds = new ArrayList<Long>();
        DataScope dataScope = DataScope.SELF;
        for (SysRoleEntity role : roles) {
            enabledRoleIds.add(role.getId());
            dataScope = higherDataScope(dataScope, role.getDataScope());
        }
        if (enabledRoleIds.isEmpty()) {
            return new PermissionProfile();
        }
        List<SysRolePermissionEntity> rolePermissions =
                rolePermissionRepository.findByRoleIdInAndTenantIdAndDeletedFalse(enabledRoleIds, user.getTenantId());
        List<Long> permissionIds = new ArrayList<Long>();
        for (SysRolePermissionEntity rolePermission : rolePermissions) {
            permissionIds.add(rolePermission.getPermissionId());
        }
        PermissionProfile profile = new PermissionProfile();
        profile.setDataScope(dataScope);
        if (permissionIds.isEmpty()) {
            return profile;
        }
        List<SysPermissionEntity> permissionEntities =
                permissionRepository.findByIdInAndTenantIdAndDeletedFalse(permissionIds, user.getTenantId());
        for (SysPermissionEntity permission : permissionEntities) {
            if (!permission.isEnabled()) {
                continue;
            }
            profile.getPermissions().add(permission.getCode());
            if (PermissionType.MENU.equals(permission.getPermissionType())) {
                profile.getMenuPermissions().add(permission.getCode());
            }
            if (PermissionType.DATA.equals(permission.getPermissionType())) {
                profile.getDataPermissions().add(permission.getCode());
            }
        }
        return profile;
    }

    private DataScope higherDataScope(DataScope current, DataScope next) {
        if (next == null) {
            return current;
        }
        if (DataScope.ALL.equals(current) || DataScope.ALL.equals(next)) {
            return DataScope.ALL;
        }
        if (DataScope.DEPARTMENT_AND_CHILD.equals(current) || DataScope.DEPARTMENT_AND_CHILD.equals(next)) {
            return DataScope.DEPARTMENT_AND_CHILD;
        }
        if (DataScope.DEPARTMENT.equals(current) || DataScope.DEPARTMENT.equals(next)) {
            return DataScope.DEPARTMENT;
        }
        return DataScope.SELF;
    }

    private String normalizeTenant(String tenantId) {
        if (tenantId == null || tenantId.trim().length() == 0) {
            return "default";
        }
        return tenantId.trim();
    }
}
