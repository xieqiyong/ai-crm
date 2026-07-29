package com.hz.crm.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.knowledge.domain.KnowledgeChangeOutboxEntity;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationEntity;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationStatus;
import com.hz.crm.knowledge.mapper.KnowledgeChangeOutboxMapper;
import com.hz.crm.knowledge.mapper.KnowledgeIndexGenerationMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeChangeReplayService {

    private static final int REPLAY_BATCH_SIZE = 100;

    @Autowired
    private KnowledgeChangeOutboxMapper changeOutboxMapper;

    @Autowired
    private KnowledgeIndexGenerationMapper generationMapper;

    @Autowired
    private KnowledgeIndexGenerationService knowledgeIndexGenerationService;

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    public synchronized void replayTo(Long tenantId, Long generationId, Long targetOutboxId) {
        if (targetOutboxId == null) {
            return;
        }
        while (true) {
            KnowledgeIndexGenerationEntity generation =
                    knowledgeIndexGenerationService.requireGeneration(tenantId, generationId);
            long fromId = generation.getReplayedOutboxId() == null
                    ? 0L
                    : generation.getReplayedOutboxId().longValue();
            if (fromId >= targetOutboxId.longValue()) {
                return;
            }
            List<KnowledgeChangeOutboxEntity> events = changeOutboxMapper.selectList(
                    Wrappers.<KnowledgeChangeOutboxEntity>lambdaQuery()
                            .eq(KnowledgeChangeOutboxEntity::getTenantId, tenantId)
                            .gt(KnowledgeChangeOutboxEntity::getId, Long.valueOf(fromId))
                            .le(KnowledgeChangeOutboxEntity::getId, targetOutboxId)
                            .orderByAsc(KnowledgeChangeOutboxEntity::getId)
                            .last("limit " + REPLAY_BATCH_SIZE));
            if (events.isEmpty()) {
                generation.setReplayedOutboxId(targetOutboxId);
                generation.setUpdatedAt(DateTimes.now());
                generationMapper.updateById(generation);
                return;
            }
            for (KnowledgeChangeOutboxEntity event : events) {
                applyEvent(generation, event);
                generation.setReplayedOutboxId(event.getId());
                generation.setUpdatedAt(DateTimes.now());
                generationMapper.updateById(generation);
            }
        }
    }

    public void replayEventToPendingGenerations(Long eventId) {
        KnowledgeChangeOutboxEntity event = changeOutboxMapper.selectById(eventId);
        if (event == null) {
            return;
        }
        List<KnowledgeIndexGenerationEntity> generations = generationMapper.selectList(
                Wrappers.<KnowledgeIndexGenerationEntity>lambdaQuery()
                        .eq(KnowledgeIndexGenerationEntity::getTenantId, event.getTenantId())
                        .isNotNull(KnowledgeIndexGenerationEntity::getSnapshotOutboxId)
                        .in(
                                KnowledgeIndexGenerationEntity::getStatus,
                                KnowledgeIndexGenerationStatus.CATCHING_UP.name(),
                                KnowledgeIndexGenerationStatus.READY.name(),
                                KnowledgeIndexGenerationStatus.ACTIVE.name()));
        for (KnowledgeIndexGenerationEntity generation : generations) {
            if (generation.getSnapshotOutboxId().longValue() >= event.getId().longValue()) {
                continue;
            }
            if (event.getSourceIndexGenerationId() != null
                    && event.getSourceIndexGenerationId().equals(generation.getId())) {
                continue;
            }
            replayTo(event.getTenantId(), generation.getId(), event.getId());
        }
    }

    private void applyEvent(
            KnowledgeIndexGenerationEntity generation,
            KnowledgeChangeOutboxEntity event) {
        if (event.getSourceIndexGenerationId() != null
                && event.getSourceIndexGenerationId().equals(generation.getId())) {
            return;
        }
        if ("DELETE".equals(event.getEventType())) {
            knowledgeDocumentService.deleteDocumentFromGeneration(
                    event.getTenantId(), event.getDocumentId(), generation.getId());
            return;
        }
        knowledgeDocumentService.rebuildDocumentVersionIntoGeneration(
                event.getTenantId(),
                event.getDocumentId(),
                event.getDocumentVersionId(),
                generation.getId());
    }
}
