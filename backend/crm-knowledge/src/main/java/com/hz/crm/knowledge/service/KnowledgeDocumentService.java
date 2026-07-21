package com.hz.crm.knowledge.service;

import com.hz.crm.common.api.PageData;
import com.hz.crm.common.api.PageQuery;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.knowledge.domain.KnowledgeDocumentEntity;
import com.hz.crm.knowledge.dto.KnowledgeDocumentRequest;
import com.hz.crm.knowledge.repository.KnowledgeDocumentRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeDocumentService {

    @Autowired
    private KnowledgeDocumentRepository documentRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(readOnly = true)
    public PageData<KnowledgeDocumentEntity> page(String tenantId, PageQuery query) {
        PageQuery safeQuery = query == null ? new PageQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<KnowledgeDocumentEntity> page = documentRepository.findByTenantIdAndDeletedFalse(tenantId, pageRequest);
        return PageData.of(
                page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), page.getContent());
    }

    @Transactional
    public KnowledgeDocumentEntity save(String tenantId, KnowledgeDocumentRequest request) {
        KnowledgeDocumentEntity entity;
        if (request.getId() == null) {
            entity = new KnowledgeDocumentEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setStatus("READY");
        } else {
            entity = documentRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.getId(), tenantId)
                    .orElseThrow(() -> new BusinessException("KB_001", "知识文档不存在"));
        }
        entity.setTitle(request.getTitle());
        entity.setSourceType(request.getSourceType());
        entity.setObjectKey(request.getObjectKey());
        entity.setContent(request.getContent());
        return documentRepository.save(entity);
    }
}
