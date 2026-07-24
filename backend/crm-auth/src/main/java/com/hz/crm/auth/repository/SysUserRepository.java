package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysUserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserRepository extends JpaRepository<SysUserEntity, Long> {

    Optional<SysUserEntity> findByUsernameAndTenantIdAndDeletedFalse(String username, Long tenantId);

    List<SysUserEntity> findByUsernameAndDeletedFalse(String username);

    List<SysUserEntity> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(Long tenantId);

    List<SysUserEntity> findByTenantIdAndIdInAndDeletedFalse(Long tenantId, List<Long> ids);

    Optional<SysUserEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    boolean existsByUsernameAndTenantIdAndDeletedFalse(String username, Long tenantId);

    long countByDeletedFalse();
}
