package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysUserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserRepository extends JpaRepository<SysUserEntity, Long> {

    Optional<SysUserEntity> findByUsernameAndTenantIdAndDeletedFalse(String username, String tenantId);

    List<SysUserEntity> findByUsernameAndDeletedFalse(String username);

    List<SysUserEntity> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(String tenantId);

    List<SysUserEntity> findByTenantIdAndIdInAndDeletedFalse(String tenantId, List<Long> ids);

    Optional<SysUserEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);

    boolean existsByUsernameAndTenantIdAndDeletedFalse(String username, String tenantId);

    long countByDeletedFalse();
}
