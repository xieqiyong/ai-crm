package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.DataScope;
import com.hz.crm.auth.domain.PermissionType;
import com.hz.crm.auth.domain.SysPermissionEntity;
import com.hz.crm.auth.domain.SysRoleEntity;
import com.hz.crm.auth.domain.SysRolePermissionEntity;
import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.domain.SysUserRoleEntity;
import com.hz.crm.auth.dto.ForgotPasswordRequest;
import com.hz.crm.auth.dto.LoginRequest;
import com.hz.crm.auth.dto.LoginResponse;
import com.hz.crm.auth.dto.PasswordResetResponse;
import com.hz.crm.auth.dto.PermissionProfile;
import com.hz.crm.auth.dto.ResetPasswordRequest;
import com.hz.crm.auth.repository.SysPermissionRepository;
import com.hz.crm.auth.repository.SysRolePermissionRepository;
import com.hz.crm.auth.repository.SysRoleRepository;
import com.hz.crm.auth.repository.SysUserRepository;
import com.hz.crm.auth.repository.SysUserRoleRepository;
import com.hz.crm.auth.security.CurrentUserContext;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.auth.security.JwtService;
import com.hz.crm.auth.security.LoginSessionService;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.redis.RedisCacheService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private LoginSessionService loginSessionService;

    @Autowired
    private RedisCacheService redisCacheService;

    @Value("${crm.auth.password-reset.ttl-seconds:900}")
    private long passwordResetTtlSeconds;

    @Value("${crm.auth.password-reset.expose-token:false}")
    private boolean exposePasswordResetToken;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (request == null) {
            throw new BusinessException("AUTH_001", "账号或密码不正确");
        }
        String username = normalizeUsername(request.getUsername());
        SysUserEntity user = resolveLoginUser(request, username);
        if (!user.isEnabled()) {
            throw new BusinessException("AUTH_002", "账号已停用");
        }
        if (request.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("AUTH_001", "账号或密码不正确");
        }
        PermissionProfile profile = loadPermissions(user);
        String sessionId = loginSessionService.nextSessionId();
        JwtPrincipal principal = jwtService.createPrincipal(user, profile, sessionId);
        String token = jwtService.createToken(principal);
        loginSessionService.save(principal, token);
        CurrentUserContext.setPrincipal(principal);
        CurrentUserContext.setToken(token);
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setTenantId(user.getTenantId());
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setSessionId(sessionId);
        response.setExpiresAt(principal.getExpiresAt());
        response.setTtlSeconds(principal.getTtlSeconds());
        response.setPermissions(new ArrayList<String>(profile.getPermissions()));
        response.setMenuPermissions(new ArrayList<String>(profile.getMenuPermissions()));
        response.setDataPermissions(new ArrayList<String>(profile.getDataPermissions()));
        response.setDataScope(profile.getDataScope().name());
        return response;
    }

    public void logout(JwtPrincipal principal) {
        loginSessionService.remove(principal);
    }

    @Transactional(readOnly = true)
    public PasswordResetResponse requestPasswordReset(ForgotPasswordRequest request) {
        PasswordResetResponse response = new PasswordResetResponse();
        response.setAccepted(true);
        response.setTtlSeconds(passwordResetTtlSeconds);
        response.setMessage("如果账号存在，系统已经生成密码重置申请");
        if (request == null || request.getUsername() == null || request.getUsername().trim().length() == 0) {
            return response;
        }
        List<SysUserEntity> users = userRepository.findByUsernameAndDeletedFalse(request.getUsername().trim());
        if (users.size() != 1 || !users.get(0).isEnabled()) {
            return response;
        }
        SysUserEntity user = users.get(0);
        String token = UUID.randomUUID().toString().replace("-", "");
        redisCacheService.setString(passwordResetKey(token), user.getTenantId() + ":" + user.getId(), passwordResetTtlSeconds);
        if (exposePasswordResetToken) {
            response.setResetTokenExposed(true);
            response.setResetToken(token);
            response.setMessage("重置码已生成，请在有效期内完成密码重置");
        }
        return response;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request == null || request.getResetToken() == null || request.getResetToken().trim().length() == 0) {
            throw new BusinessException("AUTH_RESET_001", "重置码不能为空");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            throw new BusinessException("AUTH_RESET_002", "新密码长度不能少于8位");
        }
        String key = passwordResetKey(request.getResetToken().trim());
        String value = redisCacheService.getString(key);
        if (value == null || value.trim().length() == 0 || value.indexOf(':') < 0) {
            throw new BusinessException("AUTH_RESET_003", "重置码无效或已过期");
        }
        String[] parts = value.split(":", 2);
        SysUserEntity user = userRepository
                .findByIdAndTenantIdAndDeletedFalse(Long.valueOf(parts[1]), Long.valueOf(parts[0]))
                .orElseThrow(() -> new BusinessException("AUTH_RESET_003", "重置码无效或已过期"));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        redisCacheService.delete(key);
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

    private SysUserEntity resolveLoginUser(LoginRequest request, String username) {
        Long tenantId = request.getTenantId();
        if (tenantId != null) {
            return userRepository
                    .findByUsernameAndTenantIdAndDeletedFalse(username, tenantId)
                    .orElseThrow(() -> new BusinessException("AUTH_001", "账号或密码不正确"));
        }
        List<SysUserEntity> users = userRepository.findByUsernameAndDeletedFalse(username);
        if (users.isEmpty()) {
            throw new BusinessException("AUTH_001", "账号或密码不正确");
        }
        if (users.size() > 1) {
            throw new BusinessException("AUTH_003", "账号存在多个租户，请联系管理员处理");
        }
        return users.get(0);
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

    private String normalizeUsername(String username) {
        if (username == null || username.trim().length() == 0) {
            throw new BusinessException("AUTH_001", "账号或密码不正确");
        }
        return username.trim();
    }

    private String passwordResetKey(String token) {
        return "crm:auth:password-reset:" + token;
    }
}
