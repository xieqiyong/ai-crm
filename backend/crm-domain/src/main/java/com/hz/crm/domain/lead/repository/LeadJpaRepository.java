package com.hz.crm.domain.lead.repository;

import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.LeadStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadJpaRepository extends JpaRepository<LeadEntity, Long> {

    Page<LeadEntity> findByTenantIdAndDeletedFalse(String tenantId, Pageable pageable);

    Page<LeadEntity> findByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId, Pageable pageable);

    @Query("select l from LeadEntity l where l.tenantId = :tenantId and l.deleted = false "
            + "and (:ownerId is null or l.ownerId = :ownerId) "
            + "and (:status is null or l.status = :status) "
            + "and (:keyword is null "
            + "or lower(coalesce(l.name, '')) like :keyword "
            + "or lower(coalesce(l.companyName, '')) like :keyword "
            + "or lower(coalesce(l.phone, '')) like :keyword "
            + "or lower(coalesce(l.email, '')) like :keyword "
            + "or lower(coalesce(l.source, '')) like :keyword)")
    Page<LeadEntity> search(
            @Param("tenantId") String tenantId,
            @Param("ownerId") Long ownerId,
            @Param("keyword") String keyword,
            @Param("status") LeadStatus status,
            Pageable pageable);

    Optional<LeadEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);

    long countByTenantIdAndDeletedFalse(String tenantId);

    long countByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId);

    long countByTenantIdAndStatusAndDeletedFalse(String tenantId, LeadStatus status);

    long countByTenantIdAndOwnerIdAndStatusAndDeletedFalse(String tenantId, Long ownerId, LeadStatus status);
}
