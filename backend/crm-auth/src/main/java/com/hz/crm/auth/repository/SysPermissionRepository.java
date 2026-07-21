package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysPermissionEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysPermissionRepository extends JpaRepository<SysPermissionEntity, Long> {

    List<SysPermissionEntity> findByIdInAndTenantIdAndDeletedFalse(Collection<Long> ids, String tenantId);

    List<SysPermissionEntity> findByTenantIdAndEnabledTrueAndDeletedFalse(String tenantId);
}
