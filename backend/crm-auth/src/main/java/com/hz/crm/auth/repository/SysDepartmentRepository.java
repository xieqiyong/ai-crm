package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysDepartmentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysDepartmentRepository extends JpaRepository<SysDepartmentEntity, Long> {

    List<SysDepartmentEntity> findByTenantIdAndDeletedFalseOrderBySortNoAscCreatedAtAsc(String tenantId);

    Optional<SysDepartmentEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);

    Optional<SysDepartmentEntity> findByCodeAndTenantIdAndDeletedFalse(String code, String tenantId);

    Optional<SysDepartmentEntity> findFirstByTenantIdAndParentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(String tenantId);

    boolean existsByCodeAndTenantIdAndDeletedFalse(String code, String tenantId);
}
