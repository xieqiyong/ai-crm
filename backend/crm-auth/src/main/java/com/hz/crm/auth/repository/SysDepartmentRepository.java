package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysDepartmentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysDepartmentRepository extends JpaRepository<SysDepartmentEntity, Long> {

    List<SysDepartmentEntity> findByTenantIdAndDeletedFalseOrderBySortNoAscCreatedAtAsc(Long tenantId);

    Optional<SysDepartmentEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    Optional<SysDepartmentEntity> findByCodeAndTenantIdAndDeletedFalse(String code, Long tenantId);

    Optional<SysDepartmentEntity> findFirstByTenantIdAndParentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(Long tenantId);

    boolean existsByCodeAndTenantIdAndDeletedFalse(String code, Long tenantId);
}
