package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysRoleEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysRoleRepository extends JpaRepository<SysRoleEntity, Long> {

    List<SysRoleEntity> findByIdInAndTenantIdAndEnabledTrueAndDeletedFalse(Collection<Long> ids, String tenantId);

    List<SysRoleEntity> findByTenantIdAndDeletedFalseOrderByCreatedAtAsc(String tenantId);

    List<SysRoleEntity> findByCodeAndDeletedFalse(String code);

    Optional<SysRoleEntity> findByCodeAndTenantIdAndDeletedFalse(String code, String tenantId);

    Optional<SysRoleEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);
}
