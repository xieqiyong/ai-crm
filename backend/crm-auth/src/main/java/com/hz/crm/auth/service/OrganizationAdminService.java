package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.DataScope;
import com.hz.crm.auth.domain.PermissionType;
import com.hz.crm.auth.domain.SysDepartmentEntity;
import com.hz.crm.auth.domain.SysPermissionEntity;
import com.hz.crm.auth.domain.SysRoleEntity;
import com.hz.crm.auth.domain.SysRolePermissionEntity;
import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.domain.SysUserRoleEntity;
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
import com.hz.crm.auth.repository.SysDepartmentRepository;
import com.hz.crm.auth.repository.SysPermissionRepository;
import com.hz.crm.auth.repository.SysRolePermissionRepository;
import com.hz.crm.auth.repository.SysRoleRepository;
import com.hz.crm.auth.repository.SysUserRepository;
import com.hz.crm.auth.repository.SysUserRoleRepository;
import com.hz.crm.auth.security.LoginSessionService;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAdminService {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private static final String SUPER_ADMIN_ROLE_NAME = "超级管理员";

    @Autowired
    private SysDepartmentRepository departmentRepository;

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
    private PermissionSeedService permissionSeedService;

    @Autowired
    private DepartmentSeedService departmentSeedService;

    @Autowired
    private AccountCredentialPolicy accountCredentialPolicy;

    @Autowired
    private LoginSessionService loginSessionService;

    private SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AdminOverviewResponse overview(Long tenantId) {
        permissionSeedService.seedBasePermissions(tenantId);
        departmentSeedService.ensureTenantRootDepartment(tenantId);
        AdminOverviewResponse response = new AdminOverviewResponse();
        List<SysDepartmentEntity> departments =
                departmentRepository.findByTenantIdAndDeletedFalseOrderBySortNoAscCreatedAtAsc(tenantId);
        List<SysPermissionEntity> permissions =
                permissionRepository.findByTenantIdAndDeletedFalseOrderBySortNoAscCreatedAtAsc(tenantId);
        List<SysRoleEntity> roles = roleRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtAsc(tenantId);
        List<SysUserEntity> users = userRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId);
        response.setDepartments(toDepartments(departments));
        response.setPermissions(toPermissions(permissions));
        response.setRoles(toRoles(tenantId, roles, permissions));
        response.setUsers(toUsers(tenantId, users, departments, roles));
        return response;
    }

    @Transactional
    public DepartmentResponse saveDepartment(Long tenantId, DepartmentSaveRequest request) {
        if (request == null || blank(request.getName())) {
            throw new BusinessException("ORG_DEPT_001", "部门名称不能为空");
        }
        SysDepartmentEntity entity;
        if (request.getId() == null) {
            entity = new SysDepartmentEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findDepartment(tenantId, request.getId());
        }
        entity.setParentId(request.getParentId());
        entity.setCode(resolveDepartmentCode(tenantId, request.getCode(), entity));
        entity.setName(request.getName().trim());
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        return toDepartment(departmentRepository.save(entity));
    }

    @Transactional
    public void deleteDepartment(Long tenantId, AdminIdRequest request) {
        SysDepartmentEntity department = findDepartment(tenantId, request == null ? null : request.getId());
        List<SysUserEntity> users = userRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId);
        for (SysUserEntity user : users) {
            if (department.getId().equals(user.getDepartmentId())) {
                throw new BusinessException("ORG_DEPT_003", "部门下存在用户，不能删除");
            }
        }
        department.setDeleted(true);
        departmentRepository.save(department);
    }

    @Transactional
    public PermissionResponse savePermission(Long tenantId, PermissionSaveRequest request) {
        if (request == null || blank(request.getName()) || blank(request.getPermissionType())) {
            throw new BusinessException("ORG_PERMISSION_001", "权限名称和类型不能为空");
        }
        PermissionType permissionType = resolvePermissionType(request.getPermissionType());
        SysPermissionEntity entity;
        if (request.getId() == null) {
            entity = new SysPermissionEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findPermission(tenantId, request.getId());
        }
        entity.setCode(resolvePermissionCode(tenantId, request, entity, permissionType));
        entity.setName(request.getName().trim());
        entity.setPermissionType(permissionType);
        entity.setParentId(request.getParentId());
        entity.setRoutePath(trimToNull(request.getRoutePath()));
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        return toPermission(permissionRepository.save(entity));
    }

    @Transactional
    public PermissionResponse updatePermissionStatus(Long tenantId, PermissionStatusRequest request) {
        SysPermissionEntity permission = findPermission(tenantId, request == null ? null : request.getId());
        permission.setEnabled(request.getEnabled() == null || request.getEnabled());
        return toPermission(permissionRepository.save(permission));
    }

    @Transactional
    public void deletePermission(Long tenantId, AdminIdRequest request) {
        SysPermissionEntity permission = findPermission(tenantId, request == null ? null : request.getId());
        if (rolePermissionRepository.countByPermissionIdAndTenantIdAndDeletedFalse(permission.getId(), tenantId) > 0) {
            throw new BusinessException("ORG_PERMISSION_003", "权限已被角色使用，不能删除");
        }
        permission.setDeleted(true);
        permissionRepository.save(permission);
    }

    @Transactional
    public RoleResponse saveRole(Long tenantId, RoleSaveRequest request) {
        if (request == null || blank(request.getName())) {
            throw new BusinessException("ORG_ROLE_001", "角色名称不能为空");
        }
        permissionSeedService.seedBasePermissions(tenantId);
        SysRoleEntity entity;
        if (request.getId() == null) {
            validateNewRoleIsNotSuperAdmin(request);
            entity = new SysRoleEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findRole(tenantId, request.getId());
            if (isSuperAdminRole(entity)) {
                throw new BusinessException("ORG_ROLE_006", "超级管理员角色由系统维护，不能编辑");
            }
            validateNormalRoleIsNotRenamedToSuperAdmin(request);
        }
        entity.setCode(resolveRoleCode(tenantId, request.getCode(), entity));
        entity.setName(request.getName().trim());
        entity.setDataScope(resolveDataScope(request.getDataScope()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity = roleRepository.save(entity);
        saveRolePermissions(tenantId, entity.getId(), request.getPermissionCodes());
        List<SysPermissionEntity> permissions =
                permissionRepository.findByTenantIdAndDeletedFalseOrderBySortNoAscCreatedAtAsc(tenantId);
        return toRole(tenantId, entity, buildPermissionMap(permissions));
    }

    @Transactional
    public void deleteRole(Long tenantId, AdminIdRequest request) {
        SysRoleEntity role = findRole(tenantId, request == null ? null : request.getId());
        if (isSuperAdminRole(role)) {
            throw new BusinessException("ORG_ROLE_007", "超级管理员角色不能删除");
        }
        if (userRoleRepository.countByRoleIdAndTenantIdAndDeletedFalse(role.getId(), tenantId) > 0) {
            throw new BusinessException("ORG_ROLE_003", "角色已分配用户，不能删除");
        }
        role.setDeleted(true);
        roleRepository.save(role);
    }

    @Transactional
    public UserResponse saveUser(Long tenantId, UserSaveRequest request) {
        if (request == null || blank(request.getUsername())) {
            throw new BusinessException("ORG_USER_001", "用户名不能为空");
        }
        accountCredentialPolicy.validateUsername(request.getUsername());
        SysUserEntity entity;
        boolean passwordChanged = false;
        if (request.getId() == null) {
            if (userRepository.existsByUsernameAndTenantIdAndDeletedFalse(request.getUsername().trim(), tenantId)) {
                throw new BusinessException("ORG_USER_002", "用户名已存在");
            }
            accountCredentialPolicy.validatePassword(request.getPassword());
            entity = new SysUserEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setUsername(request.getUsername().trim());
            entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            passwordChanged = true;
        } else {
            entity = userRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.getId(), tenantId)
                    .orElseThrow(() -> new BusinessException("ORG_USER_004", "用户不存在"));
            if (request.getPassword() != null && request.getPassword().trim().length() > 0) {
                accountCredentialPolicy.validatePassword(request.getPassword());
                entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                passwordChanged = true;
            }
        }
        entity.setDisplayName(blank(request.getDisplayName()) ? entity.getUsername() : request.getDisplayName().trim());
        entity.setDepartmentId(request.getDepartmentId());
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        if (!enabled && isSuperAdminUser(tenantId, entity.getId())) {
            throw new BusinessException("ORG_USER_006", "超级管理员用户不能停用");
        }
        entity.setEnabled(enabled);
        entity = userRepository.save(entity);
        if (passwordChanged && request.getId() != null) {
            loginSessionService.revokeAllSessions(tenantId, entity.getId());
        }
        saveUserRoles(tenantId, entity.getId(), request.getRoleIds());
        return findUserResponse(tenantId, entity.getId());
    }

    @Transactional
    public UserResponse updateUserStatus(Long tenantId, UserStatusRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException("ORG_USER_005", "用户编号不能为空");
        }
        SysUserEntity user = userRepository
                .findByIdAndTenantIdAndDeletedFalse(request.getId(), tenantId)
                .orElseThrow(() -> new BusinessException("ORG_USER_004", "用户不存在"));
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        if (!enabled && isSuperAdminUser(tenantId, user.getId())) {
            throw new BusinessException("ORG_USER_006", "超级管理员用户不能停用");
        }
        user.setEnabled(enabled);
        userRepository.save(user);
        return findUserResponse(tenantId, user.getId());
    }

    @Transactional
    public UserPasswordResetResponse resetUserPassword(Long tenantId, AdminIdRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException("ORG_USER_005", "用户编号不能为空");
        }
        SysUserEntity user = userRepository
                .findByIdAndTenantIdAndDeletedFalse(request.getId(), tenantId)
                .orElseThrow(() -> new BusinessException("ORG_USER_004", "用户不存在"));
        String password = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        loginSessionService.revokeAllSessions(tenantId, user.getId());
        UserPasswordResetResponse response = new UserPasswordResetResponse();
        response.setUserId(user.getId());
        response.setTemporaryPassword(password);
        return response;
    }

    private String resolveDepartmentCode(Long tenantId, String requestCode, SysDepartmentEntity entity) {
        if (!blank(requestCode)) {
            String code = requestCode.trim();
            checkDepartmentCodeUnique(tenantId, code, entity.getId());
            return code;
        }
        if (!blank(entity.getCode())) {
            return entity.getCode();
        }
        return "DEPT_" + entity.getId();
    }

    private void checkDepartmentCodeUnique(Long tenantId, String code, Long id) {
        Optional<SysDepartmentEntity> existing = departmentRepository.findByCodeAndTenantIdAndDeletedFalse(code, tenantId);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BusinessException("ORG_DEPT_002", "部门编码已存在");
        }
    }

    private String resolveRoleCode(Long tenantId, String requestCode, SysRoleEntity entity) {
        if (!blank(requestCode)) {
            String code = requestCode.trim();
            checkRoleCodeUnique(tenantId, code, entity.getId());
            return code;
        }
        if (!blank(entity.getCode())) {
            return entity.getCode();
        }
        return "ROLE_" + entity.getId();
    }

    private void checkRoleCodeUnique(Long tenantId, String code, Long id) {
        Optional<SysRoleEntity> existing = roleRepository.findByCodeAndTenantIdAndDeletedFalse(code, tenantId);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BusinessException("ORG_ROLE_002", "角色编码已存在");
        }
    }

    private PermissionType resolvePermissionType(String permissionType) {
        try {
            return PermissionType.valueOf(permissionType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("ORG_PERMISSION_004", "权限类型不正确");
        }
    }

    private String resolvePermissionCode(
            Long tenantId,
            PermissionSaveRequest request,
            SysPermissionEntity entity,
            PermissionType permissionType) {
        if (!blank(request.getCode())) {
            String code = request.getCode().trim();
            checkPermissionCodeUnique(tenantId, code, entity.getId());
            return code;
        }
        if (!blank(entity.getCode())) {
            return entity.getCode();
        }
        String code = buildPermissionCode(request, entity.getId(), permissionType);
        return ensureGeneratedPermissionCodeUnique(tenantId, code, entity.getId());
    }

    private String buildPermissionCode(PermissionSaveRequest request, Long id, PermissionType permissionType) {
        if (PermissionType.MENU.equals(permissionType)) {
            String segment = normalizeCodeSegment(request.getRoutePath(), String.valueOf(id));
            return limitText("menu." + segment, 128);
        }
        if (PermissionType.DATA.equals(permissionType)) {
            return "data:custom:" + id;
        }
        return "crm:custom:" + id;
    }

    private void checkPermissionCodeUnique(Long tenantId, String code, Long id) {
        Optional<SysPermissionEntity> existing = permissionRepository.findByCodeAndTenantIdAndDeletedFalse(code, tenantId);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BusinessException("ORG_PERMISSION_002", "权限编码已存在");
        }
    }

    private String ensureGeneratedPermissionCodeUnique(Long tenantId, String code, Long id) {
        Optional<SysPermissionEntity> existing = permissionRepository.findByCodeAndTenantIdAndDeletedFalse(code, tenantId);
        if (!existing.isPresent() || existing.get().getId().equals(id)) {
            return code;
        }
        String suffix = "." + id;
        return limitText(code, 128 - suffix.length()) + suffix;
    }

    private String normalizeCodeSegment(String value, String fallback) {
        if (blank(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", ".");
        normalized = normalized.replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        if (blank(normalized)) {
            return fallback;
        }
        return limitText(normalized, 96);
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void saveRolePermissions(Long tenantId, Long roleId, List<String> permissionCodes) {
        List<SysRolePermissionEntity> existing =
                rolePermissionRepository.findByRoleIdAndTenantIdAndDeletedFalse(roleId, tenantId);
        for (SysRolePermissionEntity item : existing) {
            item.setDeleted(true);
            rolePermissionRepository.save(item);
        }
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        List<SysPermissionEntity> permissions =
                permissionRepository.findByCodeInAndTenantIdAndDeletedFalse(new HashSet<String>(permissionCodes), tenantId);
        for (SysPermissionEntity permission : permissions) {
            SysRolePermissionEntity item = new SysRolePermissionEntity();
            item.setId(snowflakeIdGenerator.nextId());
            item.setTenantId(tenantId);
            item.setRoleId(roleId);
            item.setPermissionId(permission.getId());
            rolePermissionRepository.save(item);
        }
    }

    private void saveUserRoles(Long tenantId, Long userId, List<Long> roleIds) {
        List<SysUserRoleEntity> existing = userRoleRepository.findByUserIdAndTenantIdAndDeletedFalse(userId, tenantId);
        List<SysRoleEntity> roles = new ArrayList<SysRoleEntity>();
        if (roleIds != null && !roleIds.isEmpty()) {
            roles = roleRepository.findByIdInAndTenantIdAndEnabledTrueAndDeletedFalse(roleIds, tenantId);
        }
        validateSuperAdminUserRoleChange(tenantId, userId, existing, roles);
        for (SysUserRoleEntity item : existing) {
            item.setDeleted(true);
            userRoleRepository.save(item);
        }
        if (roles.isEmpty()) {
            return;
        }
        for (SysRoleEntity role : roles) {
            SysUserRoleEntity item = new SysUserRoleEntity();
            item.setId(snowflakeIdGenerator.nextId());
            item.setTenantId(tenantId);
            item.setUserId(userId);
            item.setRoleId(role.getId());
            userRoleRepository.save(item);
        }
    }

    private void validateNewRoleIsNotSuperAdmin(RoleSaveRequest request) {
        if (isSuperAdminRoleRequest(request)) {
            throw new BusinessException("ORG_ROLE_006", "超级管理员角色只能由系统初始化创建");
        }
    }

    private void validateNormalRoleIsNotRenamedToSuperAdmin(RoleSaveRequest request) {
        if (isSuperAdminRoleRequest(request)) {
            throw new BusinessException("ORG_ROLE_006", "普通角色不能改为超级管理员");
        }
    }

    private boolean isSuperAdminRoleRequest(RoleSaveRequest request) {
        return request != null
                && (sameCode(SUPER_ADMIN_ROLE, request.getCode())
                || SUPER_ADMIN_ROLE_NAME.equals(trimToNull(request.getName())));
    }

    private boolean isSuperAdminRole(SysRoleEntity role) {
        return role != null && sameCode(SUPER_ADMIN_ROLE, role.getCode());
    }

    private boolean isSuperAdminUser(Long tenantId, Long userId) {
        if (userId == null) {
            return false;
        }
        Optional<SysRoleEntity> superAdminRole =
                roleRepository.findByCodeAndTenantIdAndDeletedFalse(SUPER_ADMIN_ROLE, tenantId);
        if (!superAdminRole.isPresent()) {
            return false;
        }
        List<SysUserRoleEntity> userRoles = userRoleRepository.findByUserIdAndTenantIdAndDeletedFalse(userId, tenantId);
        return hasRole(userRoles, superAdminRole.get().getId());
    }

    private void validateSuperAdminUserRoleChange(
            Long tenantId,
            Long userId,
            List<SysUserRoleEntity> existing,
            List<SysRoleEntity> roles) {
        Optional<SysRoleEntity> superAdminRole =
                roleRepository.findByCodeAndTenantIdAndDeletedFalse(SUPER_ADMIN_ROLE, tenantId);
        if (!superAdminRole.isPresent()) {
            return;
        }
        Long superAdminRoleId = superAdminRole.get().getId();
        boolean hadSuperAdminRole = hasRole(existing, superAdminRoleId);
        boolean willHaveSuperAdminRole = hasSuperAdminRole(roles);
        if (hadSuperAdminRole && !willHaveSuperAdminRole) {
            throw new BusinessException("ORG_USER_007", "不能移除唯一超级管理员角色");
        }
        if (!willHaveSuperAdminRole) {
            return;
        }
        List<SysUserRoleEntity> assignments =
                userRoleRepository.findByRoleIdAndTenantIdAndDeletedFalse(superAdminRoleId, tenantId);
        for (SysUserRoleEntity assignment : assignments) {
            if (!userId.equals(assignment.getUserId())) {
                throw new BusinessException("ORG_USER_008", "系统只允许一个超级管理员用户");
            }
        }
    }

    private boolean hasRole(List<SysUserRoleEntity> userRoles, Long roleId) {
        for (SysUserRoleEntity userRole : userRoles) {
            if (roleId.equals(userRole.getRoleId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSuperAdminRole(List<SysRoleEntity> roles) {
        for (SysRoleEntity role : roles) {
            if (isSuperAdminRole(role)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameCode(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private UserResponse findUserResponse(Long tenantId, Long userId) {
        List<SysDepartmentEntity> departments =
                departmentRepository.findByTenantIdAndDeletedFalseOrderBySortNoAscCreatedAtAsc(tenantId);
        List<SysRoleEntity> roles = roleRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtAsc(tenantId);
        SysUserEntity user = userRepository
                .findByIdAndTenantIdAndDeletedFalse(userId, tenantId)
                .orElseThrow(() -> new BusinessException("ORG_USER_004", "用户不存在"));
        List<SysUserEntity> users = new ArrayList<SysUserEntity>();
        users.add(user);
        List<UserResponse> responses = toUsers(tenantId, users, departments, roles);
        return responses.isEmpty() ? new UserResponse() : responses.get(0);
    }

    private List<DepartmentResponse> toDepartments(List<SysDepartmentEntity> departments) {
        List<DepartmentResponse> responses = new ArrayList<DepartmentResponse>();
        for (SysDepartmentEntity department : departments) {
            responses.add(toDepartment(department));
        }
        return responses;
    }

    private DepartmentResponse toDepartment(SysDepartmentEntity department) {
        DepartmentResponse response = new DepartmentResponse();
        response.setId(department.getId());
        response.setParentId(department.getParentId());
        response.setCode(department.getCode());
        response.setName(department.getName());
        response.setSortNo(department.getSortNo());
        response.setEnabled(department.isEnabled());
        return response;
    }

    private List<PermissionResponse> toPermissions(List<SysPermissionEntity> permissions) {
        List<PermissionResponse> responses = new ArrayList<PermissionResponse>();
        for (SysPermissionEntity permission : permissions) {
            responses.add(toPermission(permission));
        }
        return responses;
    }

    private PermissionResponse toPermission(SysPermissionEntity permission) {
        PermissionResponse response = new PermissionResponse();
        response.setId(permission.getId());
        response.setCode(permission.getCode());
        response.setName(permission.getName());
        response.setPermissionType(permission.getPermissionType().name());
        response.setParentId(permission.getParentId());
        response.setRoutePath(permission.getRoutePath());
        response.setSortNo(permission.getSortNo());
        response.setEnabled(permission.isEnabled());
        return response;
    }

    private List<RoleResponse> toRoles(Long tenantId, List<SysRoleEntity> roles, List<SysPermissionEntity> permissions) {
        Map<Long, SysPermissionEntity> permissionMap = buildPermissionMap(permissions);
        List<RoleResponse> responses = new ArrayList<RoleResponse>();
        for (SysRoleEntity role : roles) {
            responses.add(toRole(tenantId, role, permissionMap));
        }
        return responses;
    }

    private RoleResponse toRole(Long tenantId, SysRoleEntity role, Map<Long, SysPermissionEntity> permissionMap) {
        RoleResponse response = new RoleResponse();
        response.setId(role.getId());
        response.setCode(role.getCode());
        response.setName(role.getName());
        response.setDataScope(role.getDataScope().name());
        response.setEnabled(role.isEnabled());
        response.setUserCount(userRoleRepository.countByRoleIdAndTenantIdAndDeletedFalse(role.getId(), tenantId));
        List<SysRolePermissionEntity> rolePermissions =
                rolePermissionRepository.findByRoleIdAndTenantIdAndDeletedFalse(role.getId(), tenantId);
        for (SysRolePermissionEntity rolePermission : rolePermissions) {
            SysPermissionEntity permission = permissionMap.get(rolePermission.getPermissionId());
            if (permission == null) {
                continue;
            }
            response.getPermissionCodes().add(permission.getCode());
            if (PermissionType.MENU.equals(permission.getPermissionType())) {
                response.getMenuPermissionCodes().add(permission.getCode());
            }
            if (PermissionType.DATA.equals(permission.getPermissionType())) {
                response.getDataPermissionCodes().add(permission.getCode());
            }
        }
        return response;
    }

    private List<UserResponse> toUsers(
            Long tenantId,
            List<SysUserEntity> users,
            List<SysDepartmentEntity> departments,
            List<SysRoleEntity> roles) {
        Map<Long, SysDepartmentEntity> departmentMap = new HashMap<Long, SysDepartmentEntity>();
        for (SysDepartmentEntity department : departments) {
            departmentMap.put(department.getId(), department);
        }
        Map<Long, SysRoleEntity> roleMap = new HashMap<Long, SysRoleEntity>();
        for (SysRoleEntity role : roles) {
            roleMap.put(role.getId(), role);
        }
        List<UserResponse> responses = new ArrayList<UserResponse>();
        for (SysUserEntity user : users) {
            UserResponse response = new UserResponse();
            response.setId(user.getId());
            response.setUsername(user.getUsername());
            response.setDisplayName(user.getDisplayName());
            response.setDepartmentId(user.getDepartmentId());
            response.setEnabled(user.isEnabled());
            response.setCreatedAt(user.getCreatedAt());
            SysDepartmentEntity department = departmentMap.get(user.getDepartmentId());
            if (department != null) {
                response.setDepartmentName(department.getName());
            }
            DataScope dataScope = DataScope.SELF;
            List<SysUserRoleEntity> userRoles =
                    userRoleRepository.findByUserIdAndTenantIdAndDeletedFalse(user.getId(), tenantId);
            for (SysUserRoleEntity userRole : userRoles) {
                SysRoleEntity role = roleMap.get(userRole.getRoleId());
                if (role == null) {
                    continue;
                }
                response.getRoleIds().add(role.getId());
                response.getRoleNames().add(role.getName());
                dataScope = higherDataScope(dataScope, role.getDataScope());
            }
            response.setDataScope(dataScope.name());
            responses.add(response);
        }
        return responses;
    }

    private Map<Long, SysPermissionEntity> buildPermissionMap(List<SysPermissionEntity> permissions) {
        Map<Long, SysPermissionEntity> map = new HashMap<Long, SysPermissionEntity>();
        for (SysPermissionEntity permission : permissions) {
            map.put(permission.getId(), permission);
        }
        return map;
    }

    private DataScope resolveDataScope(String dataScope) {
        if (blank(dataScope)) {
            return DataScope.SELF;
        }
        return DataScope.valueOf(dataScope.trim());
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

    private SysDepartmentEntity findDepartment(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("ORG_DEPT_004", "部门编号不能为空");
        }
        return departmentRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("ORG_DEPT_005", "部门不存在"));
    }

    private SysRoleEntity findRole(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("ORG_ROLE_004", "角色编号不能为空");
        }
        return roleRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("ORG_ROLE_005", "角色不存在"));
    }

    private SysPermissionEntity findPermission(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("ORG_PERMISSION_004", "权限编号不能为空");
        }
        return permissionRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("ORG_PERMISSION_005", "权限不存在"));
    }

    private String generateTemporaryPassword() {
        String uppercase = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lowercase = "abcdefghijkmnopqrstuvwxyz";
        String numbers = "23456789";
        String special = "!@#$%*-_";
        String all = uppercase + lowercase + numbers + special;
        StringBuilder builder = new StringBuilder();
        builder.append(uppercase.charAt(secureRandom.nextInt(uppercase.length())));
        builder.append(lowercase.charAt(secureRandom.nextInt(lowercase.length())));
        builder.append(numbers.charAt(secureRandom.nextInt(numbers.length())));
        builder.append(special.charAt(secureRandom.nextInt(special.length())));
        for (int i = 4; i < 12; i++) {
            builder.append(all.charAt(secureRandom.nextInt(all.length())));
        }
        return builder.toString();
    }

    private String trimToNull(String value) {
        if (blank(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
