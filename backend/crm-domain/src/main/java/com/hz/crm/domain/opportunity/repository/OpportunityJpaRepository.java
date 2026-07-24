package com.hz.crm.domain.opportunity.repository;

import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.OpportunityStage;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpportunityJpaRepository extends JpaRepository<OpportunityEntity, Long> {

    Page<OpportunityEntity> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    Page<OpportunityEntity> findByTenantIdAndOwnerIdAndDeletedFalse(Long tenantId, Long ownerId, Pageable pageable);

    @Query("select o from OpportunityEntity o where o.tenantId = :tenantId and o.deleted = false "
            + "and (:ownerId is null or o.ownerId = :ownerId) "
            + "and (:stage is null or o.stage = :stage) "
            + "and (:keyword is null "
            + "or lower(coalesce(o.name, '')) like :keyword "
            + "or lower(coalesce(o.remark, '')) like :keyword)")
    Page<OpportunityEntity> search(
            @Param("tenantId") Long tenantId,
            @Param("ownerId") Long ownerId,
            @Param("keyword") String keyword,
            @Param("stage") OpportunityStage stage,
            Pageable pageable);

    Optional<OpportunityEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    long countByTenantIdAndOwnerIdAndDeletedFalse(Long tenantId, Long ownerId);

    long countByTenantIdAndStageAndDeletedFalse(Long tenantId, OpportunityStage stage);

    long countByTenantIdAndOwnerIdAndStageAndDeletedFalse(Long tenantId, Long ownerId, OpportunityStage stage);

    @Query("select sum(o.amount) from OpportunityEntity o "
            + "where o.tenantId = :tenantId and o.deleted = false "
            + "and (:ownerId is null or o.ownerId = :ownerId)")
    BigDecimal sumAmount(@Param("tenantId") Long tenantId, @Param("ownerId") Long ownerId);

    @Query("select sum(o.amount) from OpportunityEntity o "
            + "where o.tenantId = :tenantId and o.deleted = false "
            + "and (:ownerId is null or o.ownerId = :ownerId) "
            + "and o.stage = :stage")
    BigDecimal sumAmountByStage(
            @Param("tenantId") Long tenantId,
            @Param("ownerId") Long ownerId,
            @Param("stage") OpportunityStage stage);
}
