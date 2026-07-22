package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysRolePermissionEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysRolePermissionRepository extends JpaRepository<SysRolePermissionEntity, Long> {

    List<SysRolePermissionEntity> findByRoleIdInAndTenantIdAndDeletedFalse(Collection<Long> roleIds, String tenantId);

    List<SysRolePermissionEntity> findByRoleIdAndTenantIdAndDeletedFalse(Long roleId, String tenantId);

    long countByRoleIdAndTenantIdAndDeletedFalse(Long roleId, String tenantId);

    long countByPermissionIdAndTenantIdAndDeletedFalse(Long permissionId, String tenantId);
}
