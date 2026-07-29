package com.hz.crm.knowledge.service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.knowledge.domain.KnowledgeChangeOutboxEntity;
import com.hz.crm.knowledge.domain.KnowledgeDocumentEntity;
import com.hz.crm.knowledge.domain.KnowledgeDocumentStatus;
import com.hz.crm.knowledge.domain.KnowledgeDocumentVersionEntity;
import com.hz.crm.knowledge.domain.KnowledgeDocumentVersionStatus;
import com.hz.crm.knowledge.domain.KnowledgeVectorStatus;
import com.hz.crm.knowledge.mapper.KnowledgeChangeOutboxMapper;
import com.hz.crm.knowledge.mapper.KnowledgeDocumentMapper;
import com.hz.crm.knowledge.mapper.KnowledgeDocumentVersionMapper;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeVersionSwitchService {

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private KnowledgeDocumentVersionMapper documentVersionMapper;

    @Autowired
    private KnowledgeChangeOutboxMapper changeOutboxMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public boolean activate(
            Long tenantId,
            Long documentId,
            Long documentVersionId,
            Long sourceIndexGenerationId,
            boolean hasRetrievalIndex,
            boolean hasVectorIndex,
            Integer vectorDimension,
            Integer chunkCount) {
        KnowledgeDocumentEntity document = lockDocument(tenantId, documentId);
        KnowledgeDocumentVersionEntity version = documentVersionMapper.selectOne(
                Wrappers.<KnowledgeDocumentVersionEntity>lambdaQuery()
                        .eq(KnowledgeDocumentVersionEntity::getTenantId, tenantId)
                        .eq(KnowledgeDocumentVersionEntity::getId, documentVersionId)
                        .eq(KnowledgeDocumentVersionEntity::getDocumentId, documentId)
                        .last("limit 1"));
        if (document == null || document.isDeleted() || version == null) {
            if (version != null) {
                version.setStatus(KnowledgeDocumentVersionStatus.SUPERSEDED.name());
                version.setUpdatedAt(DateTimes.now());
                documentVersionMapper.updateById(version);
            }
            return false;
        }
        if (!documentVersionId.equals(document.getPendingVersionId())
                || !version.getNormalizedContentHash().equals(document.getNormalizedContentHash())) {
            version.setStatus(KnowledgeDocumentVersionStatus.SUPERSEDED.name());
            version.setUpdatedAt(DateTimes.now());
            documentVersionMapper.updateById(version);
            return false;
        }
        LocalDateTime now = DateTimes.now();
        Long oldVersionId = document.getActiveVersionId();
        if (oldVersionId != null && !oldVersionId.equals(documentVersionId)) {
            KnowledgeDocumentVersionEntity oldVersion = documentVersionMapper.selectById(oldVersionId);
            if (oldVersion != null) {
                oldVersion.setStatus(KnowledgeDocumentVersionStatus.RETIRED.name());
                oldVersion.setUpdatedAt(now);
                documentVersionMapper.updateById(oldVersion);
            }
        }
        version.setStatus(KnowledgeDocumentVersionStatus.ACTIVE.name());
        version.setChunkCount(chunkCount);
        version.setVectorDimension(hasVectorIndex ? vectorDimension : null);
        version.setActivatedAt(now);
        version.setUpdatedAt(now);
        documentVersionMapper.updateById(version);

        document.setActiveVersionId(documentVersionId);
        document.setPendingVersionId(null);
        document.setIndexVersion(version.getVersionNo());
        document.setIndexHash(version.getBuildFingerprint());
        document.setChunkCount(chunkCount);
        document.setVectorDimension(hasVectorIndex ? vectorDimension : null);
        document.setEmbeddingModel(version.getEmbeddingModel());
        document.setVectorStatus(
                hasVectorIndex ? KnowledgeVectorStatus.READY.name() : KnowledgeVectorStatus.WAITING.name());
        document.setStatus(hasRetrievalIndex
                ? KnowledgeDocumentStatus.READY.name()
                : KnowledgeDocumentStatus.WAITING_VECTOR.name());
        document.setIndexedAt(now);
        document.setErrorMessage(null);
        document.setUpdatedAt(now);
        documentMapper.updateById(document);
        appendOutbox(document, version, sourceIndexGenerationId, "UPSERT");
        return true;
    }

    @Transactional
    public void appendDelete(Long tenantId, Long documentId, Long sourceIndexGenerationId) {
        KnowledgeDocumentEntity document = documentMapper.selectById(documentId);
        if (document == null || !tenantId.equals(document.getTenantId())) {
            return;
        }
        appendOutbox(document, null, sourceIndexGenerationId, "DELETE");
    }

    private KnowledgeDocumentEntity lockDocument(Long tenantId, Long documentId) {
        QueryWrapper<KnowledgeDocumentEntity> wrapper = new QueryWrapper<KnowledgeDocumentEntity>();
        wrapper.eq("id", documentId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.last("for update");
        return documentMapper.selectOne(wrapper);
    }

    private void appendOutbox(
            KnowledgeDocumentEntity document,
            KnowledgeDocumentVersionEntity version,
            Long sourceIndexGenerationId,
            String eventType) {
        LocalDateTime now = DateTimes.now();
        Long eventId = snowflakeIdGenerator.nextId();
        JSONObject payload = new JSONObject();
        payload.put("eventId", String.valueOf(eventId));
        payload.put("tenantId", String.valueOf(document.getTenantId()));
        payload.put("documentId", String.valueOf(document.getId()));
        payload.put("documentVersionId", version == null ? null : String.valueOf(version.getId()));
        payload.put(
                "sourceIndexGenerationId",
                sourceIndexGenerationId == null ? null : String.valueOf(sourceIndexGenerationId));
        payload.put("versionNo", version == null ? null : version.getVersionNo());
        payload.put("eventType", eventType);
        payload.put("occurredAt", now.toString());

        KnowledgeChangeOutboxEntity outbox = new KnowledgeChangeOutboxEntity();
        outbox.setId(eventId);
        outbox.setTenantId(document.getTenantId());
        outbox.setDocumentId(document.getId());
        outbox.setDocumentVersionId(version == null ? null : version.getId());
        outbox.setSourceIndexGenerationId(sourceIndexGenerationId);
        outbox.setEventType(eventType);
        outbox.setEventKey(document.getTenantId() + ":" + document.getId() + ":" + eventId);
        outbox.setPayloadJson(payload.toJSONString());
        outbox.setPublished(false);
        outbox.setPublishAttempts(0);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        changeOutboxMapper.insert(outbox);
    }
}
