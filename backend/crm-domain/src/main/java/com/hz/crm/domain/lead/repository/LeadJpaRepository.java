package com.hz.crm.domain.lead.repository;

import com.hz.crm.domain.lead.LeadEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadJpaRepository extends JpaRepository<LeadEntity, Long> {

    Page<LeadEntity> findByTenantIdAndDeletedFalse(String tenantId, Pageable pageable);

    Page<LeadEntity> findByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId, Pageable pageable);

    Optional<LeadEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);

    long countByTenantIdAndDeletedFalse(String tenantId);

    long countByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId);
}
