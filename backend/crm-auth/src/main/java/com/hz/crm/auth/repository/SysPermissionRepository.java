package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysPermissionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysPermissionRepository extends JpaRepository<SysPermissionEntity, Long> {

    List<SysPermissionEntity> findByIdInAndTenantIdAndDeletedFalse(Collection<Long> ids, String tenantId);

    List<SysPermissionEntity> findByTenantIdAndDeletedFalseOrderBySortNoAscCreatedAtAsc(String tenantId);

    List<SysPermissionEntity> findByTenantIdAndEnabledTrueAndDeletedFalse(String tenantId);

    List<SysPermissionEntity> findByCodeInAndTenantIdAndDeletedFalse(Collection<String> codes, String tenantId);

    Optional<SysPermissionEntity> findByCodeAndTenantIdAndDeletedFalse(String code, String tenantId);

    Optional<SysPermissionEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);
}
