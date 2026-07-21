package com.hz.crm.activity.repository;

import com.hz.crm.activity.domain.WorkflowDefinitionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, Long> {

    Optional<WorkflowDefinitionEntity> findByCodeAndTenantIdAndEnabledTrueAndDeletedFalse(String code, String tenantId);
}
