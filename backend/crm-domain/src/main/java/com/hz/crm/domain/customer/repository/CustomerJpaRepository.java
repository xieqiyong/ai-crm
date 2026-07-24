package com.hz.crm.domain.customer.repository;

import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.CustomerStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long> {

    Page<CustomerEntity> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    Page<CustomerEntity> findByTenantIdAndOwnerIdAndDeletedFalse(Long tenantId, Long ownerId, Pageable pageable);

    @Query("select c from CustomerEntity c where c.tenantId = :tenantId and c.deleted = false "
            + "and (:status is null or c.status = :status) "
            + "and (:keyword is null "
            + "or lower(coalesce(c.name, '')) like :keyword "
            + "or lower(coalesce(c.industry, '')) like :keyword "
            + "or lower(coalesce(c.contactName, '')) like :keyword "
            + "or lower(coalesce(c.contactPhone, '')) like :keyword "
            + "or lower(coalesce(c.contactEmail, '')) like :keyword)")
    Page<CustomerEntity> searchByTenantId(
            @Param("tenantId") Long tenantId,
            @Param("keyword") String keyword,
            @Param("status") CustomerStatus status,
            Pageable pageable);

    @Query("select c from CustomerEntity c where c.tenantId = :tenantId and c.ownerId = :ownerId "
            + "and c.deleted = false "
            + "and (:status is null or c.status = :status) "
            + "and (:keyword is null "
            + "or lower(coalesce(c.name, '')) like :keyword "
            + "or lower(coalesce(c.industry, '')) like :keyword "
            + "or lower(coalesce(c.contactName, '')) like :keyword "
            + "or lower(coalesce(c.contactPhone, '')) like :keyword "
            + "or lower(coalesce(c.contactEmail, '')) like :keyword)")
    Page<CustomerEntity> searchByTenantIdAndOwnerId(
            @Param("tenantId") Long tenantId,
            @Param("ownerId") Long ownerId,
            @Param("keyword") String keyword,
            @Param("status") CustomerStatus status,
            Pageable pageable);

    Optional<CustomerEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    List<CustomerEntity> findByTenantIdAndIdInAndDeletedFalse(Long tenantId, Collection<Long> ids);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    long countByTenantIdAndOwnerIdAndDeletedFalse(Long tenantId, Long ownerId);

    long countByTenantIdAndStatusAndDeletedFalse(Long tenantId, CustomerStatus status);

    long countByTenantIdAndOwnerIdAndStatusAndDeletedFalse(Long tenantId, Long ownerId, CustomerStatus status);
}
