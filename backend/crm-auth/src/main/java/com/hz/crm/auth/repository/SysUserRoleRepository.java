package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysUserRoleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserRoleRepository extends JpaRepository<SysUserRoleEntity, Long> {

    List<SysUserRoleEntity> findByUserIdAndTenantIdAndDeletedFalse(Long userId, String tenantId);

    List<SysUserRoleEntity> findByTenantIdAndDeletedFalse(String tenantId);

    List<SysUserRoleEntity> findByRoleIdAndTenantIdAndDeletedFalse(Long roleId, String tenantId);

    boolean existsByRoleIdAndTenantIdAndDeletedFalse(Long roleId, String tenantId);

    long countByRoleIdAndTenantIdAndDeletedFalse(Long roleId, String tenantId);
}
