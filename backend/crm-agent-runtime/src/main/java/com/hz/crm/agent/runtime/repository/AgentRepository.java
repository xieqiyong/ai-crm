package com.hz.crm.agent.runtime.repository;

import com.hz.crm.agent.runtime.domain.AgentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<AgentEntity, Long> {

    Page<AgentEntity> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    Optional<AgentEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    Optional<AgentEntity> findFirstByTenantIdAndSceneCodeAndEnabledTrueAndDeletedFalseOrderByUpdatedAtDesc(
            Long tenantId, String sceneCode);

    List<AgentEntity> findByTenantIdAndSceneCodeAndEnabledTrueAndDeletedFalseOrderByUpdatedAtDesc(
            Long tenantId, String sceneCode);

    Optional<AgentEntity> findFirstByTenantIdAndCodeAndEnabledTrueAndDeletedFalseOrderByUpdatedAtDesc(
            Long tenantId, String code);
}
