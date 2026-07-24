package com.hz.crm.agent.runtime.repository;

import com.hz.crm.agent.runtime.domain.ModelConfigEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelConfigRepository extends JpaRepository<ModelConfigEntity, Long> {

    List<ModelConfigEntity> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(Long tenantId);

    List<ModelConfigEntity> findByTenantIdAndDefaultConfigTrueAndDeletedFalse(Long tenantId);

    List<ModelConfigEntity> findByTenantIdAndDefaultConfigTrueAndEnabledTrueAndDeletedFalse(Long tenantId);

    List<ModelConfigEntity> findByTenantIdAndEnabledTrueAndDeletedFalseOrderByCreatedAtDesc(Long tenantId);

    Optional<ModelConfigEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);
}
