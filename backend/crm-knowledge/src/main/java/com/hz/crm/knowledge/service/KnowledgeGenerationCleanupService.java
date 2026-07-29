package com.hz.crm.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.knowledge.client.KnowledgeElasticsearchClient;
import com.hz.crm.knowledge.client.KnowledgeMilvusClient;
import com.hz.crm.knowledge.domain.KnowledgeChunkEntity;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationEntity;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationStatus;
import com.hz.crm.knowledge.mapper.KnowledgeChunkMapper;
import com.hz.crm.knowledge.mapper.KnowledgeIndexGenerationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeGenerationCleanupService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGenerationCleanupService.class);

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private KnowledgeIndexGenerationMapper generationMapper;

    @Autowired
    private KnowledgeElasticsearchClient knowledgeElasticsearchClient;

    @Autowired
    private KnowledgeMilvusClient knowledgeMilvusClient;

    @Autowired
    @Qualifier("knowledgeRebuildTaskExecutor")
    private TaskExecutor knowledgeRebuildTaskExecutor;

    public void schedule(KnowledgeIndexGenerationEntity retiredGeneration) {
        if (retiredGeneration == null) {
            return;
        }
        try {
            knowledgeRebuildTaskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    cleanup(retiredGeneration);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("知识旧索引代次清理任务提交失败，generationId={}",
                    retiredGeneration.getId(), ex);
        }
    }

    private void cleanup(KnowledgeIndexGenerationEntity generation) {
        UpdateWrapper<KnowledgeChunkEntity> wrapper = new UpdateWrapper<KnowledgeChunkEntity>();
        wrapper.eq("tenant_id", generation.getTenantId());
        wrapper.eq("index_generation_id", generation.getId());
        wrapper.eq("deleted", false);
        wrapper.set("deleted", true);
        wrapper.set("updated_at", DateTimes.now());
        chunkMapper.update(null, wrapper);
        cleanupElasticsearch(generation);
        cleanupMilvus(generation);
        log.info("知识旧索引代次清理完成，generationId={}", generation.getId());
    }

    private void cleanupElasticsearch(KnowledgeIndexGenerationEntity generation) {
        Long references = generationMapper.selectCount(
                Wrappers.<KnowledgeIndexGenerationEntity>lambdaQuery()
                        .ne(KnowledgeIndexGenerationEntity::getId, generation.getId())
                        .eq(
                                KnowledgeIndexGenerationEntity::getElasticsearchIndex,
                                generation.getElasticsearchIndex())
                        .ne(
                                KnowledgeIndexGenerationEntity::getStatus,
                                KnowledgeIndexGenerationStatus.RETIRED.name()));
        if (references.longValue() > 0L) {
            return;
        }
        try {
            knowledgeElasticsearchClient.deleteIndex(generation.getElasticsearchIndex());
        } catch (RuntimeException ex) {
            log.warn("知识旧ES索引清理失败，generationId={}", generation.getId(), ex);
        }
    }

    private void cleanupMilvus(KnowledgeIndexGenerationEntity generation) {
        Long references = generationMapper.selectCount(
                Wrappers.<KnowledgeIndexGenerationEntity>lambdaQuery()
                        .ne(KnowledgeIndexGenerationEntity::getId, generation.getId())
                        .eq(
                                KnowledgeIndexGenerationEntity::getMilvusCollection,
                                generation.getMilvusCollection())
                        .ne(
                                KnowledgeIndexGenerationEntity::getStatus,
                                KnowledgeIndexGenerationStatus.RETIRED.name()));
        if (references.longValue() > 0L) {
            return;
        }
        try {
            knowledgeMilvusClient.dropCollection(generation.getMilvusCollection());
        } catch (RuntimeException ex) {
            log.warn("知识旧Milvus集合清理失败，generationId={}", generation.getId(), ex);
        }
    }
}
