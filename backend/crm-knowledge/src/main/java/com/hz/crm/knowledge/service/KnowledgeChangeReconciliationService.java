package com.hz.crm.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationEntity;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationStatus;
import com.hz.crm.knowledge.mapper.KnowledgeIndexGenerationMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeChangeReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeChangeReconciliationService.class);

    @Autowired
    private KnowledgeIndexGenerationMapper generationMapper;

    @Autowired
    private KnowledgeIndexRebuildService knowledgeIndexRebuildService;

    @Autowired
    private KnowledgeChangeReplayService knowledgeChangeReplayService;

    @Scheduled(
            fixedDelayString = "${crm.knowledge.change.reconcile-delay-ms:3000}",
            initialDelayString = "${crm.knowledge.change.reconcile-initial-delay-ms:10000}")
    public synchronized void reconcile() {
        List<KnowledgeIndexGenerationEntity> generations = generationMapper.selectList(
                Wrappers.<KnowledgeIndexGenerationEntity>lambdaQuery()
                        .isNotNull(KnowledgeIndexGenerationEntity::getSnapshotOutboxId)
                        .in(
                                KnowledgeIndexGenerationEntity::getStatus,
                                KnowledgeIndexGenerationStatus.CATCHING_UP.name(),
                                KnowledgeIndexGenerationStatus.READY.name(),
                                KnowledgeIndexGenerationStatus.ACTIVE.name()));
        for (KnowledgeIndexGenerationEntity generation : generations) {
            try {
                Long latestOutboxId =
                        knowledgeIndexRebuildService.latestOutboxId(generation.getTenantId());
                knowledgeChangeReplayService.replayTo(
                        generation.getTenantId(), generation.getId(), latestOutboxId);
            } catch (RuntimeException ex) {
                log.warn("知识索引增量对账失败，tenantId={}，generationId={}",
                        generation.getTenantId(), generation.getId(), ex);
            }
        }
    }
}
