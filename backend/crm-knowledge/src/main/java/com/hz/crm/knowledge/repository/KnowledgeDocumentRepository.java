package com.hz.crm.knowledge.repository;

import com.hz.crm.knowledge.domain.KnowledgeDocumentEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, Long> {

    Page<KnowledgeDocumentEntity> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    Optional<KnowledgeDocumentEntity> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);
}
