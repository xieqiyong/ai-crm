package com.hz.crm.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.knowledge.client.KnowledgeElasticsearchClient;
import com.hz.crm.knowledge.client.KnowledgeEmbeddingClient;
import com.hz.crm.knowledge.client.KnowledgeMilvusClient;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationEntity;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationStatus;
import com.hz.crm.knowledge.mapper.KnowledgeIndexGenerationMapper;
import com.hz.crm.knowledge.support.KnowledgeFingerprintService;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeIndexGenerationService {

    @Autowired
    private KnowledgeIndexGenerationMapper generationMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private KnowledgeEmbeddingClient knowledgeEmbeddingClient;

    @Autowired
    private KnowledgeElasticsearchClient knowledgeElasticsearchClient;

    @Autowired
    private KnowledgeMilvusClient knowledgeMilvusClient;

    @Autowired
    private KnowledgeFingerprintService knowledgeFingerprintService;

    public synchronized KnowledgeIndexGenerationEntity requireActive(Long tenantId) {
        KnowledgeIndexGenerationEntity active = findActive(tenantId);
        if (active != null) {
            return active;
        }
        LocalDateTime now = DateTimes.now();
        KnowledgeIndexGenerationEntity generation = newGeneration(tenantId, KnowledgeIndexGenerationStatus.ACTIVE);
        generation.setProgress(100);
        generation.setMessage("初始索引代次已启用");
        generation.setActivatedAt(now);
        generation.setFinishedAt(now);
        generationMapper.insert(generation);
        return generation;
    }

    @Transactional(readOnly = true)
    public KnowledgeIndexGenerationEntity findActive(Long tenantId) {
        return generationMapper.selectOne(Wrappers.<KnowledgeIndexGenerationEntity>lambdaQuery()
                .eq(KnowledgeIndexGenerationEntity::getTenantId, tenantId)
                .eq(KnowledgeIndexGenerationEntity::getStatus, KnowledgeIndexGenerationStatus.ACTIVE.name())
                .orderByDesc(KnowledgeIndexGenerationEntity::getActivatedAt)
                .last("limit 1"));
    }

    public synchronized KnowledgeIndexGenerationEntity createBuilding(
            Long tenantId, Long snapshotOutboxId) {
        KnowledgeIndexGenerationEntity running = generationMapper.selectOne(
                Wrappers.<KnowledgeIndexGenerationEntity>lambdaQuery()
                        .eq(KnowledgeIndexGenerationEntity::getTenantId, tenantId)
                        .in(
                                KnowledgeIndexGenerationEntity::getStatus,
                                KnowledgeIndexGenerationStatus.BUILDING.name(),
                                KnowledgeIndexGenerationStatus.CATCHING_UP.name(),
                                KnowledgeIndexGenerationStatus.READY.name())
                        .orderByDesc(KnowledgeIndexGenerationEntity::getCreatedAt)
                        .last("limit 1"));
        if (running != null) {
            throw new BusinessException("KB_REBUILD_001", "当前租户已有索引重建任务");
        }
        KnowledgeIndexGenerationEntity generation =
                newGeneration(tenantId, KnowledgeIndexGenerationStatus.BUILDING);
        generation.setSnapshotOutboxId(snapshotOutboxId);
        generation.setReplayedOutboxId(snapshotOutboxId);
        generation.setProgress(0);
        generation.setMessage("绿色索引已创建，等待构建");
        generationMapper.insert(generation);
        return generation;
    }

    @Transactional
    public void activate(Long tenantId, Long generationId) {
        KnowledgeIndexGenerationEntity generation = requireGeneration(tenantId, generationId);
        if (!KnowledgeIndexGenerationStatus.READY.name().equals(generation.getStatus())) {
            throw new BusinessException("KB_REBUILD_002", "绿色索引尚未准备完成");
        }
        LocalDateTime now = DateTimes.now();
        KnowledgeIndexGenerationEntity active = findActive(tenantId);
        if (active != null && !active.getId().equals(generationId)) {
            active.setStatus(KnowledgeIndexGenerationStatus.RETIRED.name());
            active.setUpdatedAt(now);
            generationMapper.updateById(active);
        }
        generation.setStatus(KnowledgeIndexGenerationStatus.ACTIVE.name());
        generation.setProgress(100);
        generation.setMessage("绿色索引已切换为活动代次");
        generation.setActivatedAt(now);
        generation.setFinishedAt(now);
        generation.setUpdatedAt(now);
        generationMapper.updateById(generation);
    }

    @Transactional
    public void updateProgress(
            Long tenantId,
            Long generationId,
            KnowledgeIndexGenerationStatus status,
            Integer documentCount,
            Integer completedDocumentCount,
            int progress,
            String message) {
        KnowledgeIndexGenerationEntity generation = requireGeneration(tenantId, generationId);
        generation.setStatus(status.name());
        generation.setDocumentCount(documentCount);
        generation.setCompletedDocumentCount(completedDocumentCount);
        generation.setProgress(Integer.valueOf(Math.max(0, Math.min(progress, 100))));
        generation.setMessage(message);
        generation.setErrorMessage(null);
        generation.setUpdatedAt(DateTimes.now());
        generationMapper.updateById(generation);
    }

    @Transactional
    public void markFailed(Long tenantId, Long generationId, String errorMessage) {
        KnowledgeIndexGenerationEntity generation = requireGeneration(tenantId, generationId);
        generation.setStatus(KnowledgeIndexGenerationStatus.FAILED.name());
        generation.setProgress(100);
        generation.setMessage("索引重建失败，活动索引未切换");
        generation.setErrorMessage(shrink(errorMessage, 1000));
        generation.setFinishedAt(DateTimes.now());
        generation.setUpdatedAt(DateTimes.now());
        generationMapper.updateById(generation);
    }

    @Transactional(readOnly = true)
    public KnowledgeIndexGenerationEntity requireGeneration(Long tenantId, Long generationId) {
        KnowledgeIndexGenerationEntity generation = generationMapper.selectOne(
                Wrappers.<KnowledgeIndexGenerationEntity>lambdaQuery()
                        .eq(KnowledgeIndexGenerationEntity::getTenantId, tenantId)
                        .eq(KnowledgeIndexGenerationEntity::getId, generationId)
                        .last("limit 1"));
        if (generation == null) {
            throw new BusinessException("KB_REBUILD_003", "索引代次不存在");
        }
        return generation;
    }

    private KnowledgeIndexGenerationEntity newGeneration(
            Long tenantId, KnowledgeIndexGenerationStatus status) {
        Long id = snowflakeIdGenerator.nextId();
        LocalDateTime now = DateTimes.now();
        KnowledgeIndexGenerationEntity generation = new KnowledgeIndexGenerationEntity();
        generation.setId(id);
        generation.setTenantId(tenantId);
        generation.setStatus(status.name());
        generation.setElasticsearchIndex(knowledgeElasticsearchClient.generationIndexName(id));
        generation.setMilvusCollection(knowledgeMilvusClient.generationCollectionName(id));
        generation.setEmbeddingModel(
                knowledgeEmbeddingClient.enabled() ? knowledgeEmbeddingClient.model() : "DISABLED");
        generation.setVectorDimension(Integer.valueOf(Math.max(knowledgeEmbeddingClient.dimensions(), 0)));
        generation.setChunkProfileHash(knowledgeFingerprintService.chunkProfileHash());
        generation.setDocumentCount(0);
        generation.setCompletedDocumentCount(0);
        generation.setProgress(0);
        generation.setCreatedAt(now);
        generation.setUpdatedAt(now);
        return generation;
    }

    private String shrink(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
