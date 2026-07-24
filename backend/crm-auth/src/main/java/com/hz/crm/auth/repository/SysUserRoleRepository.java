package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysUserRoleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserRoleRepository extends JpaRepository<SysUserRoleEntity, Long> {

    List<SysUserRoleEntity> findByUserIdAndTenantIdAndDeletedFalse(Long userId, Long tenantId);

    List<SysUserRoleEntity> findByTenantIdAndDeletedFalse(Long tenantId);

    List<SysUserRoleEntity> findByRoleIdAndTenantIdAndDeletedFalse(Long roleId, Long tenantId);

    boolean existsByRoleIdAndTenantIdAndDeletedFalse(Long roleId, Long tenantId);

    long countByRoleIdAndTenantIdAndDeletedFalse(Long roleId, Long tenantId);
}
