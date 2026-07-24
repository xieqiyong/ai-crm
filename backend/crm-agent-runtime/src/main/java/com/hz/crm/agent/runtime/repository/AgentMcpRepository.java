package com.hz.crm.agent.runtime.repository;

import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMcpRepository extends JpaRepository<AgentMcpEntity, Long> {

    List<AgentMcpEntity> findByAgentIdAndTenantIdAndEnabledTrueAndDeletedFalse(Long agentId, Long tenantId);

    List<AgentMcpEntity> findByAgentIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(Long agentId, Long tenantId);

    Optional<AgentMcpEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);
}
