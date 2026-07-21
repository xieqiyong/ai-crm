package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysRolePermissionEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysRolePermissionRepository extends JpaRepository<SysRolePermissionEntity, Long> {

    List<SysRolePermissionEntity> findByRoleIdInAndTenantIdAndDeletedFalse(Collection<Long> roleIds, String tenantId);
}
