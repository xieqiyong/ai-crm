package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysPermissionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysPermissionRepository extends JpaRepository<SysPermissionEntity, Long> {

    List<SysPermissionEntity> findByIdInAndTenantIdAndDeletedFalse(Collection<Long> ids, Long tenantId);

    List<SysPermissionEntity> findByTenantIdAndDeletedFalseOrderBySortNoAscCreatedAtAsc(Long tenantId);

    List<SysPermissionEntity> findByTenantIdAndEnabledTrueAndDeletedFalse(Long tenantId);

    List<SysPermissionEntity> findByCodeInAndTenantIdAndDeletedFalse(Collection<String> codes, Long tenantId);

    Optional<SysPermissionEntity> findByCodeAndTenantIdAndDeletedFalse(String code, Long tenantId);

    Optional<SysPermissionEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);
}
