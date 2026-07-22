package com.hz.crm.domain.customer.repository;

import com.hz.crm.domain.customer.CustomerEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long> {

    Page<CustomerEntity> findByTenantIdAndDeletedFalse(String tenantId, Pageable pageable);

    Page<CustomerEntity> findByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId, Pageable pageable);

    @Query("select c from CustomerEntity c where c.tenantId = :tenantId and c.deleted = false "
            + "and (:keyword is null or lower(c.name) like lower(concat('%', :keyword, '%')) "
            + "or lower(c.industry) like lower(concat('%', :keyword, '%')) "
            + "or lower(c.contactName) like lower(concat('%', :keyword, '%')) "
            + "or lower(c.contactPhone) like lower(concat('%', :keyword, '%')) "
            + "or lower(c.contactEmail) like lower(concat('%', :keyword, '%')))")
    Page<CustomerEntity> searchByTenantId(
            @Param("tenantId") String tenantId, @Param("keyword") String keyword, Pageable pageable);

    @Query("select c from CustomerEntity c where c.tenantId = :tenantId and c.ownerId = :ownerId "
            + "and c.deleted = false "
            + "and (:keyword is null or lower(c.name) like lower(concat('%', :keyword, '%')) "
            + "or lower(c.industry) like lower(concat('%', :keyword, '%')) "
            + "or lower(c.contactName) like lower(concat('%', :keyword, '%')) "
            + "or lower(c.contactPhone) like lower(concat('%', :keyword, '%')) "
            + "or lower(c.contactEmail) like lower(concat('%', :keyword, '%')))")
    Page<CustomerEntity> searchByTenantIdAndOwnerId(
            @Param("tenantId") String tenantId,
            @Param("ownerId") Long ownerId,
            @Param("keyword") String keyword,
            Pageable pageable);

    Optional<CustomerEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);

    long countByTenantIdAndDeletedFalse(String tenantId);

    long countByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId);
}
