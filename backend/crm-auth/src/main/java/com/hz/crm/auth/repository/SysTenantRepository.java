package com.hz.crm.auth.repository;

import com.hz.crm.auth.domain.SysTenantEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysTenantRepository extends JpaRepository<SysTenantEntity, Long> {

    List<SysTenantEntity> findByDeletedFalseOrderByCreatedAtAsc();
}
