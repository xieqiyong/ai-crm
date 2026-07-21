package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysRoleEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysRoleRepository extends JpaRepository<SysRoleEntity, Long> {

    List<SysRoleEntity> findByIdInAndTenantIdAndEnabledTrueAndDeletedFalse(Collection<Long> ids, String tenantId);

    Optional<SysRoleEntity> findByCodeAndTenantIdAndDeletedFalse(String code, String tenantId);
}
