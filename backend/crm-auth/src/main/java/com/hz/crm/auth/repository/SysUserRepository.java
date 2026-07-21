package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserRepository extends JpaRepository<SysUserEntity, Long> {

    Optional<SysUserEntity> findByUsernameAndTenantIdAndDeletedFalse(String username, String tenantId);

    long countByDeletedFalse();
}
