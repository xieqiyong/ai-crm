package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.DataScope;
import com.hz.crm.auth.domain.PermissionType;
import com.hz.crm.auth.domain.SysPermissionEntity;
import com.hz.crm.auth.domain.SysRoleEntity;
import com.hz.crm.auth.domain.SysRolePermissionEntity;
import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.domain.SysUserRoleEntity;
import com.hz.crm.auth.dto.InstallStatusResponse;
import com.hz.crm.auth.dto.LoginRequest;
import com.hz.crm.auth.dto.LoginResponse;
import com.hz.crm.auth.dto.SetupSuperAdminRequest;
import com.hz.crm.auth.repository.SysPermissionRepository;
import com.hz.crm.auth.repository.SysRolePermissionRepository;
import com.hz.crm.auth.repository.SysRoleRepository;
import com.hz.crm.auth.repository.SysUserRepository;
import com.hz.crm.auth.repository.SysUserRoleRepository;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstallationService {

    private static final String DEFAULT_TENANT = "default";

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysPermissionRepository permissionRepository;

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
        String tenantId = normalizeTenant(request.getTenantId());
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new BusinessException("INSTALL_002", "超管密码长度不能少于8位");
        }
        List<SysPermissionEntity> permissions = preparePermissions(tenantId);
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
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(resolveDisplayName(request));
        userRepository.save(user);
        SysUserRoleEntity userRole = new SysUserRoleEntity();
        userRole.setId(snowflakeIdGenerator.nextId());
        userRole.setTenantId(tenantId);
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRoleRepository.save(userRole);
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setTenantId(tenantId);
        loginRequest.setUsername(request.getUsername());
        loginRequest.setPassword(request.getPassword());
        return authApplicationService.login(loginRequest);
    }

    private boolean hasSuperAdmin() {
        SysRoleEntity role = roleRepository
                .findByCodeAndTenantIdAndDeletedFalse(SUPER_ADMIN_ROLE, DEFAULT_TENANT)
                .orElse(null);
        if (role == null) {
            return false;
        }
        return userRoleRepository.existsByRoleIdAndTenantIdAndDeletedFalse(role.getId(), DEFAULT_TENANT);
    }

    private List<SysPermissionEntity> preparePermissions(String tenantId) {
        List<SysPermissionEntity> existing = permissionRepository.findByTenantIdAndEnabledTrueAndDeletedFalse(tenantId);
        Map<String, SysPermissionEntity> existsMap = new HashMap<String, SysPermissionEntity>();
        for (SysPermissionEntity permission : existing) {
            existsMap.put(permission.getCode(), permission);
        }
        List<SysPermissionEntity> result = new ArrayList<SysPermissionEntity>();
        String[][] definitions = permissionDefinitions();
        for (int i = 0; i < definitions.length; i++) {
            String code = definitions[i][0];
            SysPermissionEntity permission = existsMap.get(code);
            if (permission == null) {
                permission = new SysPermissionEntity();
                permission.setId(snowflakeIdGenerator.nextId());
                permission.setTenantId(tenantId);
                permission.setCode(code);
                permission.setName(definitions[i][1]);
                permission.setPermissionType(PermissionType.valueOf(definitions[i][2]));
                permission.setRoutePath(definitions[i][3]);
                permission.setSortNo(i + 1);
                permission = permissionRepository.save(permission);
            }
            result.add(permission);
        }
        return result;
    }

    private String[][] permissionDefinitions() {
        return new String[][] {
            {"*", "全部操作权限", "ACTION", ""},
            {"menu.dashboard", "工作台菜单", "MENU", "dashboard"},
            {"menu.leads", "线索菜单", "MENU", "leads"},
            {"menu.customers", "客户菜单", "MENU", "customers"},
            {"menu.opportunities", "商机菜单", "MENU", "opportunities"},
            {"menu.followups", "跟进菜单", "MENU", "followups"},
            {"menu.tasks", "任务菜单", "MENU", "tasks"},
            {"menu.assistant", "AI助手菜单", "MENU", "assistant"},
            {"menu.knowledge", "知识库菜单", "MENU", "knowledge"},
            {"menu.organization", "组织权限菜单", "MENU", "organization"},
            {"menu.settings", "系统设置菜单", "MENU", "settings"},
            {"crm:dashboard:view", "工作台查看", "ACTION", ""},
            {"crm:lead:view", "线索查看", "ACTION", ""},
            {"crm:lead:manage", "线索管理", "ACTION", ""},
            {"crm:lead:create", "线索创建", "ACTION", ""},
            {"crm:lead:export", "线索导出", "ACTION", ""},
            {"crm:customer:view", "客户查看", "ACTION", ""},
            {"crm:customer:manage", "客户管理", "ACTION", ""},
            {"crm:customer:edit", "客户编辑", "ACTION", ""},
            {"crm:opportunity:view", "商机查看", "ACTION", ""},
            {"crm:opportunity:manage", "商机管理", "ACTION", ""},
            {"crm:opportunity:create", "商机创建", "ACTION", ""},
            {"crm:assistant:use", "AI助手使用", "ACTION", ""},
            {"crm:knowledge:manage", "知识库管理", "ACTION", ""},
            {"crm:org:view", "组织权限查看", "ACTION", ""},
            {"crm:org:manage", "组织权限管理", "ACTION", ""},
            {"crm:settings:view", "系统设置查看", "ACTION", ""},
            {"crm:workflow:manage", "流程管理", "ACTION", ""},
            {"crm:observability:view", "可观测查看", "ACTION", ""},
            {"data:all", "全部数据", "DATA", ""},
            {"data:department", "部门数据", "DATA", ""},
            {"data:self", "本人数据", "DATA", ""}
        };
    }

    private String resolveDisplayName(SetupSuperAdminRequest request) {
        if (request.getDisplayName() == null || request.getDisplayName().trim().length() == 0) {
            return request.getUsername();
        }
        return request.getDisplayName().trim();
    }

    private String normalizeTenant(String tenantId) {
        if (tenantId == null || tenantId.trim().length() == 0) {
            return DEFAULT_TENANT;
        }
        return tenantId.trim();
    }
}
