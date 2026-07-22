package com.hz.crm.agent.runtime.repository;

import com.hz.crm.agent.runtime.domain.ModelConfigEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelConfigRepository extends JpaRepository<ModelConfigEntity, Long> {

    List<ModelConfigEntity> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(String tenantId);

    List<ModelConfigEntity> findByTenantIdAndDefaultConfigTrueAndDeletedFalse(String tenantId);

    Optional<ModelConfigEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);
}
