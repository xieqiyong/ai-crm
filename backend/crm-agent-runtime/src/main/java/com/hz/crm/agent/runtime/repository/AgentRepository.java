package com.hz.crm.agent.runtime.repository;

import com.hz.crm.agent.runtime.domain.AgentEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<AgentEntity, Long> {

    Page<AgentEntity> findByTenantIdAndDeletedFalse(String tenantId, Pageable pageable);

    Optional<AgentEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);
}
