package com.hz.crm.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.knowledge.domain.KnowledgeDocumentEntity;
import com.hz.crm.knowledge.domain.KnowledgeDocumentStatus;
import com.hz.crm.knowledge.domain.KnowledgeDocumentVersionEntity;
import com.hz.crm.knowledge.domain.KnowledgeDocumentVersionStatus;
import com.hz.crm.knowledge.domain.KnowledgeIngestTaskEntity;
import com.hz.crm.knowledge.mapper.KnowledgeDocumentMapper;
import com.hz.crm.knowledge.mapper.KnowledgeDocumentVersionMapper;
import com.hz.crm.knowledge.mapper.KnowledgeIngestTaskMapper;
import com.hz.crm.knowledge.support.KnowledgeFingerprintService;
import com.hz.crm.knowledge.support.KnowledgeIngestPreparation;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeIngestTaskCoordinator {

    private static final String TASK_PENDING = "PENDING";

    private static final String TASK_RUNNING = "RUNNING";

    private static final String TASK_SKIPPED = "SKIPPED";

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private KnowledgeDocumentVersionMapper documentVersionMapper;

    @Autowired
    private KnowledgeIngestTaskMapper ingestTaskMapper;

    @Autowired
    private KnowledgeFingerprintService knowledgeFingerprintService;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Value("${crm.knowledge.ingest.stale-task-minutes:120}")
    private long staleTaskMinutes;

    @Transactional
    public KnowledgeIngestPreparation prepare(Long tenantId, Long documentId, boolean force) {
        KnowledgeDocumentEntity document = lockDocument(tenantId, documentId);
        KnowledgeIngestTaskEntity running = findRunningTask(tenantId, documentId);
        if (running != null) {
            if (!isStale(running)) {
                return preparation(running, false, false);
            }
            expireStaleTask(document, running);
        }
        String buildFingerprint = knowledgeFingerprintService.buildFingerprint(document);
        KnowledgeDocumentVersionEntity active = findActiveVersion(tenantId, document);
        if (!force
                && active != null
                && buildFingerprint.equals(active.getBuildFingerprint())
                && KnowledgeDocumentVersionStatus.ACTIVE.name().equals(active.getStatus())) {
            KnowledgeIngestTaskEntity task = createSkippedTask(tenantId, document, active);
            return preparation(task, false, true);
        }
        int versionNo = nextVersionNo(tenantId, documentId);
        KnowledgeDocumentVersionEntity version =
                createVersion(tenantId, document, versionNo, buildFingerprint);
        documentVersionMapper.insert(version);
        KnowledgeIngestTaskEntity task = createPendingTask(tenantId, document, version, force);
        ingestTaskMapper.insert(task);
        document.setPendingVersionId(version.getId());
        document.setErrorMessage(null);
        if (document.getActiveVersionId() == null) {
            document.setStatus(KnowledgeDocumentStatus.INDEXING.name());
        }
        document.setUpdatedAt(DateTimes.now());
        documentMapper.updateById(document);
        return preparation(task, true, false);
    }

    private KnowledgeDocumentEntity lockDocument(Long tenantId, Long documentId) {
        QueryWrapper<KnowledgeDocumentEntity> wrapper = new QueryWrapper<KnowledgeDocumentEntity>();
        wrapper.eq("id", documentId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.last("for update");
        KnowledgeDocumentEntity document = documentMapper.selectOne(wrapper);
        if (document == null) {
            throw new BusinessException("KB_005", "知识文档不存在");
        }
        if (document.getContent() == null || document.getContent().trim().length() == 0) {
            throw new BusinessException("KB_002", "知识内容不能为空");
        }
        return document;
    }

    private KnowledgeIngestTaskEntity findRunningTask(Long tenantId, Long documentId) {
        return ingestTaskMapper.selectOne(Wrappers.<KnowledgeIngestTaskEntity>lambdaQuery()
                .eq(KnowledgeIngestTaskEntity::getTenantId, tenantId)
                .eq(KnowledgeIngestTaskEntity::getDocumentId, documentId)
                .eq(KnowledgeIngestTaskEntity::getDeleted, Boolean.FALSE)
                .in(KnowledgeIngestTaskEntity::getStatus, TASK_PENDING, TASK_RUNNING)
                .orderByDesc(KnowledgeIngestTaskEntity::getCreatedAt)
                .last("limit 1"));
    }

    private boolean isStale(KnowledgeIngestTaskEntity task) {
        LocalDateTime updatedAt = task.getUpdatedAt();
        if (updatedAt == null) {
            return true;
        }
        long minutes = Math.max(staleTaskMinutes, 1L);
        return updatedAt.isBefore(DateTimes.now().minusMinutes(minutes));
    }

    private void expireStaleTask(
            KnowledgeDocumentEntity document, KnowledgeIngestTaskEntity task) {
        LocalDateTime now = DateTimes.now();
        task.setStatus("FAILED");
        task.setStage("任务超时");
        task.setProgress(100);
        task.setMessage("历史入库任务长时间未更新，已允许重新提交");
        task.setErrorMessage("任务超过允许执行时间");
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        ingestTaskMapper.updateById(task);
        if (task.getDocumentVersionId() != null) {
            KnowledgeDocumentVersionEntity version =
                    documentVersionMapper.selectById(task.getDocumentVersionId());
            if (version != null) {
                version.setStatus(KnowledgeDocumentVersionStatus.FAILED.name());
                version.setErrorMessage("入库任务超时");
                version.setUpdatedAt(now);
                documentVersionMapper.updateById(version);
            }
        }
        if (task.getDocumentVersionId() != null
                && task.getDocumentVersionId().equals(document.getPendingVersionId())) {
            document.setPendingVersionId(null);
        }
        document.setUpdatedAt(now);
        documentMapper.updateById(document);
    }

    private KnowledgeDocumentVersionEntity findActiveVersion(
            Long tenantId, KnowledgeDocumentEntity document) {
        if (document.getActiveVersionId() == null) {
            return null;
        }
        return documentVersionMapper.selectOne(Wrappers.<KnowledgeDocumentVersionEntity>lambdaQuery()
                .eq(KnowledgeDocumentVersionEntity::getTenantId, tenantId)
                .eq(KnowledgeDocumentVersionEntity::getId, document.getActiveVersionId())
                .last("limit 1"));
    }

    private int nextVersionNo(Long tenantId, Long documentId) {
        KnowledgeDocumentVersionEntity latest = documentVersionMapper.selectOne(
                Wrappers.<KnowledgeDocumentVersionEntity>lambdaQuery()
                        .eq(KnowledgeDocumentVersionEntity::getTenantId, tenantId)
                        .eq(KnowledgeDocumentVersionEntity::getDocumentId, documentId)
                        .orderByDesc(KnowledgeDocumentVersionEntity::getVersionNo)
                        .last("limit 1"));
        return latest == null ? 1 : latest.getVersionNo().intValue() + 1;
    }

    private KnowledgeDocumentVersionEntity createVersion(
            Long tenantId,
            KnowledgeDocumentEntity document,
            int versionNo,
            String buildFingerprint) {
        LocalDateTime now = DateTimes.now();
        KnowledgeDocumentVersionEntity version = new KnowledgeDocumentVersionEntity();
        version.setId(snowflakeIdGenerator.nextId());
        version.setTenantId(tenantId);
        version.setDocumentId(document.getId());
        version.setVersionNo(Integer.valueOf(versionNo));
        version.setSourceKey(document.getSourceKey());
        version.setRawFileHash(document.getRawFileHash());
        version.setNormalizedContentHash(document.getNormalizedContentHash());
        version.setBuildFingerprint(buildFingerprint);
        version.setStatus(KnowledgeDocumentVersionStatus.CREATED.name());
        version.setTitle(document.getTitle());
        version.setSourceType(document.getSourceType());
        version.setCategory(document.getCategory());
        version.setTags(document.getTags());
        version.setSourceUrl(document.getSourceUrl());
        version.setObjectKey(document.getObjectKey());
        version.setContentSnapshot(document.getContent());
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        return version;
    }

    private KnowledgeIngestTaskEntity createPendingTask(
            Long tenantId,
            KnowledgeDocumentEntity document,
            KnowledgeDocumentVersionEntity version,
            boolean force) {
        LocalDateTime now = DateTimes.now();
        KnowledgeIngestTaskEntity task = new KnowledgeIngestTaskEntity();
        task.setId(snowflakeIdGenerator.nextId());
        task.setTenantId(tenantId);
        task.setDocumentId(document.getId());
        task.setDocumentVersionId(version.getId());
        task.setIdempotencyKey(version.getBuildFingerprint());
        task.setForce(Boolean.valueOf(force));
        task.setStatus(TASK_PENDING);
        task.setStage("排队中");
        task.setProgress(0);
        task.setMessage("知识版本入库任务已提交，等待后台执行");
        task.setIndexVersion(version.getVersionNo());
        task.setIndexHash(version.getBuildFingerprint());
        task.setDeleted(Boolean.FALSE);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private KnowledgeIngestTaskEntity createSkippedTask(
            Long tenantId,
            KnowledgeDocumentEntity document,
            KnowledgeDocumentVersionEntity active) {
        LocalDateTime now = DateTimes.now();
        KnowledgeIngestTaskEntity task = new KnowledgeIngestTaskEntity();
        task.setId(snowflakeIdGenerator.nextId());
        task.setTenantId(tenantId);
        task.setDocumentId(document.getId());
        task.setDocumentVersionId(active.getId());
        task.setIdempotencyKey(active.getBuildFingerprint());
        task.setForce(Boolean.FALSE);
        task.setStatus(TASK_SKIPPED);
        task.setStage("跳过入库");
        task.setProgress(100);
        task.setMessage("标准化内容和索引配置均未变化，已跳过重复入库");
        task.setIndexVersion(active.getVersionNo());
        task.setIndexHash(active.getBuildFingerprint());
        task.setChunkCount(active.getChunkCount());
        task.setVectorDimension(active.getVectorDimension());
        task.setEmbeddingModel(active.getEmbeddingModel());
        task.setFinishedAt(now);
        task.setDeleted(Boolean.FALSE);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        ingestTaskMapper.insert(task);
        return task;
    }

    private KnowledgeIngestPreparation preparation(
            KnowledgeIngestTaskEntity task, boolean scheduled, boolean skipped) {
        KnowledgeIngestPreparation preparation = new KnowledgeIngestPreparation();
        preparation.setTask(task);
        preparation.setScheduled(scheduled);
        preparation.setSkipped(skipped);
        return preparation;
    }
}
