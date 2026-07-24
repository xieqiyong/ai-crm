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

    Page<LeadEntity> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    Page<LeadEntity> findByTenantIdAndOwnerIdAndDeletedFalse(Long tenantId, Long ownerId, Pageable pageable);

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
            @Param("tenantId") Long tenantId,
            @Param("ownerId") Long ownerId,
            @Param("keyword") String keyword,
            @Param("status") LeadStatus status,
            Pageable pageable);

    Optional<LeadEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    long countByTenantIdAndOwnerIdAndDeletedFalse(Long tenantId, Long ownerId);

    long countByTenantIdAndStatusAndDeletedFalse(Long tenantId, LeadStatus status);

    long countByTenantIdAndOwnerIdAndStatusAndDeletedFalse(Long tenantId, Long ownerId, LeadStatus status);
}
