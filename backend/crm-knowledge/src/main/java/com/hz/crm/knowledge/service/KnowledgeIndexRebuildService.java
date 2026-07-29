package com.hz.crm.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.knowledge.domain.KnowledgeChangeOutboxEntity;
import com.hz.crm.knowledge.domain.KnowledgeDocumentEntity;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationEntity;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationStatus;
import com.hz.crm.knowledge.dto.KnowledgeIndexGenerationResponse;
import com.hz.crm.knowledge.mapper.KnowledgeChangeOutboxMapper;
import com.hz.crm.knowledge.mapper.KnowledgeDocumentMapper;
import com.hz.crm.knowledge.mapper.KnowledgeIndexGenerationMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.task.TaskExecutor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIndexRebuildService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexRebuildService.class);

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private KnowledgeChangeOutboxMapper changeOutboxMapper;

    @Autowired
    private KnowledgeIndexGenerationMapper generationMapper;

    @Autowired
    private KnowledgeIndexGenerationService knowledgeIndexGenerationService;

    @Autowired
    private KnowledgeChangeReplayService knowledgeChangeReplayService;

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    @Autowired
    private KnowledgeGenerationCleanupService knowledgeGenerationCleanupService;

    @Autowired
    @Qualifier("knowledgeRebuildTaskExecutor")
    private TaskExecutor knowledgeRebuildTaskExecutor;

    public KnowledgeIndexGenerationResponse start(Long tenantId) {
        Long snapshotOutboxId = latestOutboxId(tenantId);
        KnowledgeIndexGenerationEntity generation =
                knowledgeIndexGenerationService.createBuilding(tenantId, snapshotOutboxId);
        try {
            scheduleRebuild(tenantId, generation.getId());
        } catch (RuntimeException ex) {
            knowledgeIndexGenerationService.markFailed(tenantId, generation.getId(), ex.getMessage());
            throw new BusinessException("KB_REBUILD_005", "索引重建任务提交失败");
        }
        return toResponse(generation);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        List<KnowledgeIndexGenerationEntity> generations = generationMapper.selectList(
                Wrappers.<KnowledgeIndexGenerationEntity>lambdaQuery()
                        .in(
                                KnowledgeIndexGenerationEntity::getStatus,
                                KnowledgeIndexGenerationStatus.BUILDING.name(),
                                KnowledgeIndexGenerationStatus.CATCHING_UP.name(),
                                KnowledgeIndexGenerationStatus.READY.name())
                        .orderByAsc(KnowledgeIndexGenerationEntity::getCreatedAt));
        for (KnowledgeIndexGenerationEntity generation : generations) {
            try {
                scheduleRebuild(generation.getTenantId(), generation.getId());
            } catch (RuntimeException ex) {
                log.warn("知识索引重建任务恢复失败，generationId={}", generation.getId(), ex);
            }
        }
    }

    public KnowledgeIndexGenerationResponse detail(Long tenantId, Long generationId) {
        return toResponse(knowledgeIndexGenerationService.requireGeneration(tenantId, generationId));
    }

    public KnowledgeIndexGenerationResponse current(Long tenantId) {
        KnowledgeIndexGenerationEntity generation = generationMapper.selectOne(
                Wrappers.<KnowledgeIndexGenerationEntity>lambdaQuery()
                        .eq(KnowledgeIndexGenerationEntity::getTenantId, tenantId)
                        .orderByDesc(KnowledgeIndexGenerationEntity::getCreatedAt)
                        .last("limit 1"));
        return generation == null ? null : toResponse(generation);
    }

    public Long latestOutboxId(Long tenantId) {
        KnowledgeChangeOutboxEntity latest = changeOutboxMapper.selectOne(
                Wrappers.<KnowledgeChangeOutboxEntity>lambdaQuery()
                        .eq(KnowledgeChangeOutboxEntity::getTenantId, tenantId)
                        .orderByDesc(KnowledgeChangeOutboxEntity::getId)
                        .last("limit 1"));
        return latest == null ? Long.valueOf(0L) : latest.getId();
    }

    private void runRebuild(Long tenantId, Long generationId) {
        boolean activated = false;
        try {
            KnowledgeIndexGenerationEntity generation =
                    knowledgeIndexGenerationService.requireGeneration(tenantId, generationId);
            if (KnowledgeIndexGenerationStatus.ACTIVE.name().equals(generation.getStatus())
                    || KnowledgeIndexGenerationStatus.RETIRED.name().equals(generation.getStatus())
                    || KnowledgeIndexGenerationStatus.FAILED.name().equals(generation.getStatus())) {
                return;
            }
            int total = generation.getDocumentCount() == null
                    ? 0
                    : generation.getDocumentCount().intValue();
            int completed = generation.getCompletedDocumentCount() == null
                    ? 0
                    : generation.getCompletedDocumentCount().intValue();
            if (KnowledgeIndexGenerationStatus.BUILDING.name().equals(generation.getStatus())) {
                List<KnowledgeDocumentEntity> documents = documentMapper.selectList(
                        Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                                .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                                .eq(KnowledgeDocumentEntity::isDeleted, false)
                                .isNotNull(KnowledgeDocumentEntity::getActiveVersionId)
                                .orderByAsc(KnowledgeDocumentEntity::getId));
                total = documents.size();
                completed = 0;
                knowledgeIndexGenerationService.updateProgress(
                        tenantId,
                        generationId,
                        KnowledgeIndexGenerationStatus.BUILDING,
                        Integer.valueOf(total),
                        Integer.valueOf(0),
                        2,
                        "绿色索引开始构建");
                log.info("知识绿色索引开始构建，tenantId={}，generationId={}，documentCount={}",
                        tenantId, generationId, total);
                for (KnowledgeDocumentEntity document : documents) {
                    knowledgeDocumentService.rebuildDocumentVersionIntoGeneration(
                            tenantId,
                            document.getId(),
                            document.getActiveVersionId(),
                            generationId);
                    completed++;
                    int progress = 2 + (int) Math.floor(completed * 83D / Math.max(total, 1));
                    knowledgeIndexGenerationService.updateProgress(
                            tenantId,
                            generationId,
                            KnowledgeIndexGenerationStatus.BUILDING,
                            Integer.valueOf(total),
                            Integer.valueOf(completed),
                            progress,
                            "绿色索引构建中：" + completed + "/" + total);
                    log.info("知识绿色索引文档构建完成，generationId={}，documentId={}，progress={}/{}",
                            generationId, document.getId(), completed, total);
                }
            }
            knowledgeIndexGenerationService.updateProgress(
                    tenantId,
                    generationId,
                    KnowledgeIndexGenerationStatus.CATCHING_UP,
                    Integer.valueOf(total),
                    Integer.valueOf(completed),
                    88,
                    "基础数据构建完成，开始补偿增量变更");
            log.info("知识绿色索引进入增量追平，tenantId={}，generationId={}",
                    tenantId, generationId);
            catchUpToLatest(tenantId, generationId);
            knowledgeIndexGenerationService.updateProgress(
                    tenantId,
                    generationId,
                    KnowledgeIndexGenerationStatus.READY,
                    Integer.valueOf(total),
                    Integer.valueOf(completed),
                    98,
                    "绿色索引已追平，准备切换");
            catchUpToLatest(tenantId, generationId);
            KnowledgeIndexGenerationEntity oldGeneration =
                    knowledgeIndexGenerationService.findActive(tenantId);
            knowledgeIndexGenerationService.activate(tenantId, generationId);
            activated = true;
            try {
                catchUpToLatest(tenantId, generationId);
            } catch (RuntimeException ex) {
                log.warn("知识新索引切换后首次增量对账失败，generationId={}", generationId, ex);
            }
            if (oldGeneration != null && !generationId.equals(oldGeneration.getId())) {
                knowledgeGenerationCleanupService.schedule(oldGeneration);
            }
            log.info("知识库蓝绿索引重建完成，tenantId={}，generationId={}", tenantId, generationId);
        } catch (RuntimeException ex) {
            log.warn("知识库蓝绿索引重建失败，tenantId={}，generationId={}", tenantId, generationId, ex);
            if (!activated) {
                knowledgeIndexGenerationService.markFailed(tenantId, generationId, ex.getMessage());
            }
        }
    }

    private void scheduleRebuild(Long tenantId, Long generationId) {
        knowledgeRebuildTaskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                runRebuild(tenantId, generationId);
            }
        });
    }

    private void catchUpToLatest(Long tenantId, Long generationId) {
        for (int index = 0; index < 3; index++) {
            Long targetId = latestOutboxId(tenantId);
            knowledgeChangeReplayService.replayTo(tenantId, generationId, targetId);
            Long latestId = latestOutboxId(tenantId);
            if (targetId.equals(latestId)) {
                return;
            }
        }
    }

    private KnowledgeIndexGenerationResponse toResponse(KnowledgeIndexGenerationEntity entity) {
        KnowledgeIndexGenerationResponse response = new KnowledgeIndexGenerationResponse();
        response.setId(String.valueOf(entity.getId()));
        response.setStatus(entity.getStatus());
        response.setElasticsearchIndex(entity.getElasticsearchIndex());
        response.setMilvusCollection(entity.getMilvusCollection());
        response.setEmbeddingModel(entity.getEmbeddingModel());
        response.setVectorDimension(entity.getVectorDimension());
        response.setChunkProfileHash(entity.getChunkProfileHash());
        response.setSnapshotOutboxId(
                entity.getSnapshotOutboxId() == null ? null : String.valueOf(entity.getSnapshotOutboxId()));
        response.setReplayedOutboxId(
                entity.getReplayedOutboxId() == null ? null : String.valueOf(entity.getReplayedOutboxId()));
        response.setDocumentCount(entity.getDocumentCount());
        response.setCompletedDocumentCount(entity.getCompletedDocumentCount());
        response.setProgress(entity.getProgress());
        response.setMessage(entity.getMessage());
        response.setErrorMessage(entity.getErrorMessage());
        response.setActivatedAt(entity.getActivatedAt());
        response.setFinishedAt(entity.getFinishedAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
