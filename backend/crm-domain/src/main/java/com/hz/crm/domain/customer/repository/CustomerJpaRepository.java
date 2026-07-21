package com.hz.crm.domain.customer.repository;

import com.hz.crm.domain.customer.CustomerEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long> {

    Page<CustomerEntity> findByTenantIdAndDeletedFalse(String tenantId, Pageable pageable);

    Page<CustomerEntity> findByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId, Pageable pageable);

    Optional<CustomerEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);

    long countByTenantIdAndDeletedFalse(String tenantId);

    long countByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId);
}
