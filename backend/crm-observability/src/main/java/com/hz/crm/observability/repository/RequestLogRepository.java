package com.hz.crm.observability.repository;

import com.hz.crm.observability.domain.RequestLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestLogRepository extends JpaRepository<RequestLogEntity, Long> {

    Page<RequestLogEntity> findByTenantId(Long tenantId, Pageable pageable);
}
