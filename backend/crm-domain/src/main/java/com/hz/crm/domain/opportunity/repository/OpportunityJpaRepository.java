package com.hz.crm.domain.opportunity.repository;

import com.hz.crm.domain.opportunity.OpportunityEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityJpaRepository extends JpaRepository<OpportunityEntity, Long> {

    Page<OpportunityEntity> findByTenantIdAndDeletedFalse(String tenantId, Pageable pageable);

    Page<OpportunityEntity> findByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId, Pageable pageable);

    Optional<OpportunityEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);

    long countByTenantIdAndDeletedFalse(String tenantId);

    long countByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId);
}
