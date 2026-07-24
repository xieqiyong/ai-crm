package com.hz.crm.agent.runtime.repository;

import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSkillRepository extends JpaRepository<AgentSkillEntity, Long> {

    List<AgentSkillEntity> findByAgentIdAndTenantIdAndEnabledTrueAndDeletedFalse(Long agentId, Long tenantId);

    List<AgentSkillEntity> findByAgentIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(Long agentId, Long tenantId);

    Optional<AgentSkillEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);
}
