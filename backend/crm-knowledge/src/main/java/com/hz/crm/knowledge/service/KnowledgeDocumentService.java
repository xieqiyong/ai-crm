package com.hz.crm.knowledge.service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.knowledge.client.KnowledgeElasticsearchClient;
import com.hz.crm.knowledge.client.KnowledgeEmbeddingClient;
import com.hz.crm.knowledge.client.KnowledgeMilvusClient;
import com.hz.crm.knowledge.config.KnowledgeHybridSearchProperties;
import com.hz.crm.knowledge.domain.KnowledgeChunkEntity;
import com.hz.crm.knowledge.domain.KnowledgeDocumentEntity;
import com.hz.crm.knowledge.domain.KnowledgeDocumentStatus;
import com.hz.crm.knowledge.domain.KnowledgeDocumentVersionEntity;
import com.hz.crm.knowledge.domain.KnowledgeDocumentVersionStatus;
import com.hz.crm.knowledge.domain.KnowledgeIndexGenerationEntity;
import com.hz.crm.knowledge.domain.KnowledgeIngestEventEntity;
import com.hz.crm.knowledge.domain.KnowledgeIngestTaskEntity;
import com.hz.crm.knowledge.domain.KnowledgeVectorStatus;
import com.hz.crm.knowledge.dto.KnowledgeDocumentQuery;
import com.hz.crm.knowledge.dto.KnowledgeDocumentRequest;
import com.hz.crm.knowledge.dto.KnowledgeDocumentResponse;
import com.hz.crm.knowledge.dto.KnowledgeIngestEventResponse;
import com.hz.crm.knowledge.dto.KnowledgeIngestRequest;
import com.hz.crm.knowledge.dto.KnowledgeIngestResponse;
import com.hz.crm.knowledge.dto.KnowledgeIngestTaskResponse;
import com.hz.crm.knowledge.dto.KnowledgeSearchHit;
import com.hz.crm.knowledge.dto.KnowledgeSearchRequest;
import com.hz.crm.knowledge.dto.KnowledgeSearchResponse;
import com.hz.crm.knowledge.mapper.KnowledgeChunkMapper;
import com.hz.crm.knowledge.mapper.KnowledgeDocumentMapper;
import com.hz.crm.knowledge.mapper.KnowledgeDocumentVersionMapper;
import com.hz.crm.knowledge.mapper.KnowledgeIngestEventMapper;
import com.hz.crm.knowledge.mapper.KnowledgeIngestTaskMapper;
import com.hz.crm.knowledge.support.KnowledgeFingerprintService;
import com.hz.crm.knowledge.support.KnowledgeIngestPreparation;
import com.hz.crm.knowledge.support.KnowledgeTextChunk;
import com.hz.crm.knowledge.support.KnowledgeTextSplitter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    private static final String TASK_RUNNING = "RUNNING";

    private static final String TASK_SUCCESS = "SUCCESS";

    private static final String TASK_SKIPPED = "SKIPPED";

    private static final String TASK_FAILED = "FAILED";

    private static final String EVENT_START = "START";

    private static final String EVENT_SUCCESS = "SUCCESS";

    private static final String EVENT_FAILED = "FAILED";

    private static final String EVENT_SKIPPED = "SKIPPED";

    private static final String EVENT_INFO = "INFO";

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private KnowledgeDocumentVersionMapper documentVersionMapper;

    @Autowired
    private KnowledgeIngestTaskMapper ingestTaskMapper;

    @Autowired
    private KnowledgeIngestEventMapper ingestEventMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private KnowledgeTextSplitter knowledgeTextSplitter;

    @Autowired
    private KnowledgeEmbeddingClient knowledgeEmbeddingClient;

    @Autowired
    private KnowledgeElasticsearchClient knowledgeElasticsearchClient;

    @Autowired
    private KnowledgeMilvusClient knowledgeMilvusClient;

    @Autowired
    private KnowledgeHybridSearchProperties hybridSearchProperties;

    @Autowired
    private KnowledgeFingerprintService knowledgeFingerprintService;

    @Autowired
    private KnowledgeIngestTaskCoordinator knowledgeIngestTaskCoordinator;

    @Autowired
    private KnowledgeIndexGenerationService knowledgeIndexGenerationService;

    @Autowired
    private KnowledgeVersionSwitchService knowledgeVersionSwitchService;

    @Autowired
    @Qualifier("knowledgeIngestTaskExecutor")
    private TaskExecutor knowledgeIngestTaskExecutor;

    @Transactional(readOnly = true)
    public PageData<KnowledgeDocumentResponse> page(Long tenantId, KnowledgeDocumentQuery query) {
        KnowledgeDocumentQuery safeQuery = query == null ? new KnowledgeDocumentQuery() : query;
        long total = documentMapper.selectCount(buildDocumentWrapper(tenantId, safeQuery));
        QueryWrapper<KnowledgeDocumentEntity> wrapper = buildDocumentWrapper(tenantId, safeQuery);
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        int offset = (pageNo - 1) * pageSize;
        wrapper.orderByDesc("created_at").last("limit " + pageSize + " offset " + offset);
        List<KnowledgeDocumentEntity> entities = documentMapper.selectList(wrapper);
        List<KnowledgeDocumentResponse> records = new ArrayList<KnowledgeDocumentResponse>();
        for (KnowledgeDocumentEntity entity : entities) {
            records.add(toResponse(entity, false));
        }
        return PageData.of(total, pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocumentResponse detail(Long tenantId, Long id) {
        return toResponse(findDocument(tenantId, id), true);
    }

    @Transactional
    public KnowledgeDocumentResponse save(Long tenantId, KnowledgeDocumentRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BusinessException("KB_001", "知识标题不能为空");
        }
        KnowledgeDocumentEntity entity;
        String normalizedContent = knowledgeFingerprintService.normalizeContent(request.getContent());
        String normalizedContentHash = knowledgeFingerprintService.normalizedContentHash(normalizedContent);
        LocalDateTime now = DateTimes.now();
        if (request.getId() == null) {
            Long documentId = snowflakeIdGenerator.nextId();
            String sourceKey = knowledgeFingerprintService.resolveSourceKey(request, documentId);
            entity = findDocumentBySourceKey(tenantId, sourceKey);
            if (entity == null) {
                entity = new KnowledgeDocumentEntity();
                entity.setId(documentId);
                entity.setTenantId(tenantId);
                entity.setSourceKey(sourceKey);
                entity.setStatus(KnowledgeDocumentStatus.DRAFT.name());
                entity.setVectorStatus(KnowledgeVectorStatus.WAITING.name());
                entity.setChunkCount(0);
                entity.setIndexVersion(0);
                entity.setCreatedAt(now);
            }
        } else {
            entity = findDocument(tenantId, request.getId());
            String sourceKey = hasSourceIdentity(request)
                    ? knowledgeFingerprintService.resolveSourceKey(request, entity.getId())
                    : entity.getSourceKey();
            validateSourceKeyOwner(tenantId, entity.getId(), sourceKey);
            entity.setSourceKey(sourceKey);
        }
        String oldContentHash = entity.getNormalizedContentHash();
        entity.setUpdatedAt(now);
        entity.setTitle(request.getTitle().trim());
        entity.setSourceType(trimToNull(request.getSourceType()));
        entity.setCategory(trimToNull(request.getCategory()));
        entity.setTags(trimToNull(request.getTags()));
        entity.setSourceUrl(trimToNull(request.getSourceUrl()));
        entity.setObjectKey(trimToNull(request.getObjectKey()));
        entity.setRawFileHash(trimToNull(request.getRawFileHash()));
        entity.setNormalizedContentHash(normalizedContentHash);
        entity.setContent(trimToNull(normalizedContent));
        entity.setErrorMessage(null);
        if (StringUtils.hasText(oldContentHash) && !oldContentHash.equals(normalizedContentHash)) {
            entity.setPendingVersionId(null);
        }
        if (entity.getActiveVersionId() == null) {
            entity.setStatus(KnowledgeDocumentStatus.DRAFT.name());
            entity.setVectorStatus(KnowledgeVectorStatus.WAITING.name());
        }
        if (documentMapper.selectById(entity.getId()) == null) {
            documentMapper.insert(entity);
        } else {
            documentMapper.updateById(entity);
        }
        return toResponse(entity, true);
    }

    @Transactional
    public KnowledgeDocumentResponse reuseImportWhenRawUnchanged(
            Long tenantId, KnowledgeDocumentRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getRawFileHash())
                || (!StringUtils.hasText(request.getSourceKey())
                        && !StringUtils.hasText(request.getSourceUrl())
                        && !StringUtils.hasText(request.getObjectKey()))) {
            return null;
        }
        String sourceKey = knowledgeFingerprintService.resolveSourceKey(request, Long.valueOf(0L));
        KnowledgeDocumentEntity existing = findDocumentBySourceKey(tenantId, sourceKey);
        if (existing == null
                || existing.getActiveVersionId() == null
                || !request.getRawFileHash().equals(existing.getRawFileHash())) {
            return null;
        }
        request.setId(existing.getId());
        request.setSourceKey(existing.getSourceKey());
        request.setContent(existing.getContent());
        return save(tenantId, request);
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        KnowledgeDocumentEntity entity = findDocument(tenantId, id);
        KnowledgeIndexGenerationEntity generation = knowledgeIndexGenerationService.findActive(tenantId);
        LocalDateTime now = DateTimes.now();
        entity.setDeleted(true);
        entity.setPendingVersionId(null);
        entity.setUpdatedAt(now);
        documentMapper.updateById(entity);
        UpdateWrapper<KnowledgeChunkEntity> wrapper = new UpdateWrapper<KnowledgeChunkEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("document_id", id);
        wrapper.set("deleted", true);
        wrapper.set("updated_at", now);
        chunkMapper.update(null, wrapper);
        knowledgeVersionSwitchService.appendDelete(
                tenantId, id, generation == null ? null : generation.getId());
        cleanupExternalIndex(tenantId, id);
    }

    public KnowledgeIngestResponse ingest(Long tenantId, Long id) {
        return ingest(tenantId, id, false);
    }

    public KnowledgeIngestResponse ingest(Long tenantId, KnowledgeIngestRequest request) {
        if (request == null) {
            throw new BusinessException("KB_004", "知识文档编号不能为空");
        }
        boolean force = Boolean.TRUE.equals(request.getForce());
        return ingest(tenantId, request.getId(), force);
    }

    public KnowledgeIngestResponse ingest(Long tenantId, Long id, boolean force) {
        KnowledgeIngestPreparation preparation = knowledgeIngestTaskCoordinator.prepare(tenantId, id, force);
        KnowledgeIngestTaskEntity task = preparation.getTask();
        KnowledgeDocumentEntity document = findDocument(tenantId, id);
        if (preparation.isScheduled()) {
            appendEvent(task.getId(), tenantId, id, "排队中", EVENT_START, task.getMessage(), null, null);
        } else if (preparation.isSkipped()) {
            appendEvent(task.getId(), tenantId, id, "跳过入库", EVENT_SKIPPED, task.getMessage(), null, null);
        }
        try {
            if (preparation.isScheduled()) {
                knowledgeIngestTaskExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        runIngestTask(task.getId(), tenantId, id);
                    }
                });
            }
        } catch (RuntimeException ex) {
            markTaskFailed(
                    task.getId(),
                    tenantId,
                    id,
                    task.getDocumentVersionId(),
                    task.getIndexGenerationId(),
                    ex,
                    System.currentTimeMillis());
        }
        KnowledgeIngestTaskEntity currentTask = findTask(tenantId, task.getId());
        KnowledgeIngestResponse response = toIngestResponse(document);
        response.setTaskId(String.valueOf(currentTask.getId()));
        response.setAsyncTask(Boolean.valueOf(
                preparation.isScheduled()
                        || "PENDING".equals(currentTask.getStatus())
                        || TASK_RUNNING.equals(currentTask.getStatus())));
        response.setStatus(currentTask.getStatus());
        response.setStage(currentTask.getStage());
        response.setProgress(currentTask.getProgress());
        response.setSkipped(Boolean.valueOf(preparation.isSkipped()));
        response.setMessage(currentTask.getMessage());
        return response;
    }

    @Transactional(readOnly = true)
    public KnowledgeIngestTaskResponse ingestTask(Long tenantId, Long taskId) {
        KnowledgeIngestTaskEntity task = findTask(tenantId, taskId);
        QueryWrapper<KnowledgeIngestEventEntity> wrapper = new QueryWrapper<KnowledgeIngestEventEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("task_id", taskId);
        wrapper.orderByAsc("created_at");
        List<KnowledgeIngestEventEntity> events = ingestEventMapper.selectList(wrapper);
        return toTaskResponse(task, events);
    }

    @Transactional(readOnly = true)
    public KnowledgeSearchResponse search(Long tenantId, KnowledgeSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.getQuery())) {
            throw new BusinessException("KB_SEARCH_001", "检索内容不能为空");
        }
        int topK = safeTopK(request.getTopK());
        KnowledgeSearchResponse response = new KnowledgeSearchResponse();
        response.setQuery(request.getQuery().trim());
        response.setTopK(topK);
        response.setEmbeddingEnabled(knowledgeEmbeddingClient.enabled());
        response.setMilvusEnabled(knowledgeMilvusClient.enabled());
        response.setElasticsearchEnabled(knowledgeElasticsearchClient.enabled());

        SearchTune tune = resolveSearchTune(request, topK);
        response.setSearchMode("HYBRID_RRF");
        response.setVectorCandidates(tune.vectorCandidates);
        response.setKeywordCandidates(tune.keywordCandidates);
        response.setDatabaseCandidates(tune.databaseCandidates);
        response.setVectorWeight(tune.vectorWeight);
        response.setKeywordWeight(tune.keywordWeight);
        response.setDatabaseWeight(tune.databaseWeight);
        response.setMinScore(tune.minScore);

        KnowledgeIndexGenerationEntity generation = knowledgeIndexGenerationService.findActive(tenantId);
        if (generation == null) {
            response.setDatabaseFallbackUsed(false);
            response.setHits(new ArrayList<KnowledgeSearchHit>());
            response.setMessage("当前租户尚未建立可用知识索引");
            return response;
        }
        List<KnowledgeSearchHit> vectorHits = tune.vectorWeight <= 0D
                ? new ArrayList<KnowledgeSearchHit>()
                : collectMilvusHits(tenantId, generation, request, tune.vectorCandidates);
        List<KnowledgeSearchHit> keywordHits =
                collectElasticsearchHits(tenantId, generation, request, tune.keywordCandidates);
        List<KnowledgeSearchHit> databaseHits = new ArrayList<KnowledgeSearchHit>();
        boolean shouldUseDatabase = !tune.databaseFallbackOnly || (vectorHits.isEmpty() && keywordHits.isEmpty());
        if (shouldUseDatabase) {
            databaseHits = collectDatabaseHits(tenantId, generation, request, tune.databaseCandidates);
        }
        response.setDatabaseFallbackUsed(shouldUseDatabase);
        response.setHits(mergeHybridHits(vectorHits, keywordHits, databaseHits, tune, topK));
        response.setMessage(resolveSearchMessage(response));
        return response;
    }

    public void rebuildDocumentVersionIntoGeneration(
            Long tenantId,
            Long documentId,
            Long documentVersionId,
            Long generationId) {
        KnowledgeIndexGenerationEntity generation =
                knowledgeIndexGenerationService.requireGeneration(tenantId, generationId);
        cleanupGenerationDocument(tenantId, documentId, generation);
        KnowledgeDocumentEntity document = documentMapper.selectById(documentId);
        if (document == null
                || !tenantId.equals(document.getTenantId())
                || document.isDeleted()
                || documentVersionId == null) {
            return;
        }
        KnowledgeDocumentVersionEntity version = findVersion(tenantId, documentId, documentVersionId);
        List<KnowledgeTextChunk> chunks = knowledgeTextSplitter.split(version.getContentSnapshot());
        if (chunks.isEmpty()) {
            throw new BusinessException("KB_REBUILD_004", "知识版本内容无法切分");
        }
        int indexVersion = version.getVersionNo().intValue();
        String indexHash = version.getBuildFingerprint();
        for (KnowledgeTextChunk textChunk : chunks) {
            KnowledgeChunkEntity chunk = buildChunk(
                    tenantId, document, version, generation, textChunk, indexVersion, indexHash);
            List<Float> embedding = null;
            if (shouldBuildVector()) {
                embedding = knowledgeEmbeddingClient.embed(textChunk.getContent());
                chunk.setVectorStatus(KnowledgeVectorStatus.READY.name());
                chunk.setVectorDimension(Integer.valueOf(embedding.size()));
                chunk.setEmbeddingModel(knowledgeEmbeddingClient.model());
            }
            chunkMapper.insert(chunk);
            if (knowledgeElasticsearchClient.enabled()) {
                knowledgeElasticsearchClient.index(chunk, generation.getElasticsearchIndex());
                chunk.setEsIndexed(true);
            }
            if (embedding != null) {
                knowledgeMilvusClient.index(chunk, embedding, generation.getMilvusCollection());
                chunk.setMilvusIndexed(true);
            }
            chunk.setUpdatedAt(DateTimes.now());
            chunkMapper.updateById(chunk);
        }
    }

    public void deleteDocumentFromGeneration(
            Long tenantId, Long documentId, Long generationId) {
        KnowledgeIndexGenerationEntity generation =
                knowledgeIndexGenerationService.requireGeneration(tenantId, generationId);
        cleanupGenerationDocument(tenantId, documentId, generation);
    }

    private void runIngestTask(Long taskId, Long tenantId, Long documentId) {
        long startMillis = System.currentTimeMillis();
        markTaskRunning(taskId, tenantId, documentId, startMillis);
        try {
            KnowledgeDocumentEntity document = findDocument(tenantId, documentId);
            KnowledgeIngestTaskEntity task = findTask(tenantId, taskId);
            KnowledgeDocumentVersionEntity version = findVersion(
                    tenantId, documentId, task.getDocumentVersionId());
            KnowledgeIndexGenerationEntity generation = knowledgeIndexGenerationService.requireActive(tenantId);
            task.setIndexGenerationId(generation.getId());
            task.setUpdatedAt(DateTimes.now());
            ingestTaskMapper.updateById(task);
            updateTaskProgress(taskId, tenantId, documentId, "读取文档", 8, "已读取知识文档", startMillis);
            List<KnowledgeTextChunk> chunks =
                    splitDocument(taskId, tenantId, document, version, startMillis);
            prepareDocumentForIngest(taskId, tenantId, document, version, startMillis);
            KnowledgeIngestResponse response =
                    doIngest(taskId, tenantId, document, version, generation, chunks, startMillis);
            if (TASK_SKIPPED.equals(response.getStatus())) {
                markTaskSuperseded(taskId, tenantId, document, response, startMillis);
                return;
            }
            markTaskSuccess(taskId, tenantId, document, response, startMillis);
        } catch (RuntimeException ex) {
            markTaskFailed(
                    taskId,
                    tenantId,
                    documentId,
                    findTaskVersionId(tenantId, taskId),
                    findTaskGenerationId(tenantId, taskId),
                    ex,
                    startMillis);
        }
    }

    private void markTaskRunning(Long taskId, Long tenantId, Long documentId, long startMillis) {
        LocalDateTime now = DateTimes.now();
        KnowledgeIngestTaskEntity task = new KnowledgeIngestTaskEntity();
        task.setId(taskId);
        task.setStatus(TASK_RUNNING);
        task.setStage("启动任务");
        task.setProgress(3);
        task.setMessage("知识入库任务开始执行");
        task.setStartedAt(now);
        task.setUpdatedAt(now);
        ingestTaskMapper.updateById(task);
        appendEvent(taskId, tenantId, documentId, "启动任务", EVENT_START, "知识入库任务开始执行", null, startMillis);
    }

    private List<KnowledgeTextChunk> splitDocument(
            Long taskId,
            Long tenantId,
            KnowledgeDocumentEntity document,
            KnowledgeDocumentVersionEntity version,
            long startMillis) {
        updateTaskProgress(taskId, tenantId, document.getId(), "文档切分", 15, "开始切分知识文档", startMillis);
        updateVersionStatus(version, KnowledgeDocumentVersionStatus.CHUNKING, null);
        List<KnowledgeTextChunk> chunks = knowledgeTextSplitter.split(version.getContentSnapshot());
        if (chunks.isEmpty()) {
            throw new BusinessException("KB_003", "知识内容无法切分");
        }
        JSONObject detail = new JSONObject();
        detail.put("chunkCount", chunks.size());
        detail.put("splitter", knowledgeTextSplitter.profile());
        updateTaskProgress(
                taskId,
                tenantId,
                document.getId(),
                "文档切分",
                25,
                "文档切分完成，共生成" + chunks.size() + "个分片",
                detail,
                startMillis);
        return chunks;
    }

    private void prepareDocumentForIngest(
            Long taskId,
            Long tenantId,
            KnowledgeDocumentEntity document,
            KnowledgeDocumentVersionEntity version,
            long startMillis) {
        updateTaskProgress(taskId, tenantId, document.getId(), "准备索引", 30, "开始构建候选版本", startMillis);
        LocalDateTime now = DateTimes.now();
        version.setStatus(KnowledgeDocumentVersionStatus.INDEXING.name());
        version.setErrorMessage(null);
        version.setUpdatedAt(now);
        documentVersionMapper.updateById(version);
    }

    private KnowledgeIngestResponse doIngest(
            Long taskId,
            Long tenantId,
            KnowledgeDocumentEntity document,
            KnowledgeDocumentVersionEntity version,
            KnowledgeIndexGenerationEntity generation,
            List<KnowledgeTextChunk> chunks,
            long startMillis) {
        int indexVersion = version.getVersionNo().intValue();
        String indexHash = version.getBuildFingerprint();
        updateTaskProgress(taskId, tenantId, document.getId(), "构建候选版本", 35, "旧版本继续提供检索，开始写入候选版本", startMillis);
        cleanupVersionIndex(tenantId, document.getId(), version.getId(), generation);
        boolean hasRetrievalIndex = false;
        boolean hasVectorIndex = false;
        Integer vectorDimension = null;
        int total = chunks.size();
        for (KnowledgeTextChunk textChunk : chunks) {
            int current = textChunk.getChunkIndex().intValue() + 1;
            KnowledgeChunkEntity chunk = buildChunk(
                    tenantId, document, version, generation, textChunk, indexVersion, indexHash);
            List<Float> embedding = null;
            if (shouldBuildVector()) {
                updateChunkProgress(
                        taskId, tenantId, document.getId(), "向量化", current, total, "生成分片向量", startMillis);
                embedding = knowledgeEmbeddingClient.embed(textChunk.getContent());
                chunk.setVectorStatus(KnowledgeVectorStatus.READY.name());
                chunk.setVectorDimension(embedding.size());
                chunk.setEmbeddingModel(knowledgeEmbeddingClient.model());
                vectorDimension = Integer.valueOf(embedding.size());
            }
            chunkMapper.insert(chunk);
            if (knowledgeElasticsearchClient.enabled()) {
                updateChunkProgress(
                        taskId, tenantId, document.getId(), "写入ES", current, total, "写入ES索引", startMillis);
                knowledgeElasticsearchClient.index(chunk, generation.getElasticsearchIndex());
                chunk.setEsIndexed(true);
                hasRetrievalIndex = true;
            }
            if (embedding != null) {
                updateChunkProgress(
                        taskId, tenantId, document.getId(), "写入Milvus", current, total, "写入Milvus向量库", startMillis);
                knowledgeMilvusClient.index(chunk, embedding, generation.getMilvusCollection());
                chunk.setMilvusIndexed(true);
                hasRetrievalIndex = true;
                hasVectorIndex = true;
            }
            chunk.setUpdatedAt(DateTimes.now());
            chunkMapper.updateById(chunk);
            appendChunkEvent(taskId, tenantId, document.getId(), chunk, current, total, startMillis);
        }
        version.setStatus(KnowledgeDocumentVersionStatus.READY.name());
        version.setChunkCount(Integer.valueOf(chunks.size()));
        version.setVectorDimension(hasVectorIndex ? vectorDimension : null);
        version.setEmbeddingModel(hasVectorIndex ? knowledgeEmbeddingClient.model() : null);
        version.setReadyAt(DateTimes.now());
        version.setUpdatedAt(DateTimes.now());
        documentVersionMapper.updateById(version);
        boolean activated = knowledgeVersionSwitchService.activate(
                tenantId,
                document.getId(),
                version.getId(),
                generation.getId(),
                hasRetrievalIndex,
                hasVectorIndex,
                vectorDimension,
                Integer.valueOf(chunks.size()));
        if (!activated) {
            cleanupVersionIndex(tenantId, document.getId(), version.getId(), generation);
            KnowledgeIngestResponse skipped = toIngestResponse(findDocument(tenantId, document.getId()));
            skipped.setSkipped(true);
            skipped.setStatus(TASK_SKIPPED);
            skipped.setMessage("文档内容在构建期间已更新，本次候选版本不再切换");
            return skipped;
        }
        scheduleRetiredVersionCleanup(
                tenantId, document.getId(), version.getId(), generation);
        document = findDocument(tenantId, document.getId());
        KnowledgeIngestResponse response = toIngestResponse(document);
        response.setSkipped(false);
        response.setMessage(resolveIngestMessage(hasRetrievalIndex, hasVectorIndex));
        return response;
    }

    private void updateTaskProgress(
            Long taskId, Long tenantId, Long documentId, String stage, int progress, String message, long startMillis) {
        updateTaskProgress(taskId, tenantId, documentId, stage, progress, message, null, startMillis);
    }

    private void updateTaskProgress(
            Long taskId,
            Long tenantId,
            Long documentId,
            String stage,
            int progress,
            String message,
            JSONObject detail,
            long startMillis) {
        LocalDateTime now = DateTimes.now();
        KnowledgeIngestTaskEntity task = new KnowledgeIngestTaskEntity();
        task.setId(taskId);
        task.setStatus(TASK_RUNNING);
        task.setStage(stage);
        task.setProgress(safeProgress(progress));
        task.setMessage(message);
        task.setUpdatedAt(now);
        ingestTaskMapper.updateById(task);
        appendEvent(taskId, tenantId, documentId, stage, EVENT_INFO, message, detail, startMillis);
    }

    private void updateChunkProgress(
            Long taskId,
            Long tenantId,
            Long documentId,
            String stage,
            int current,
            int total,
            String action,
            long startMillis) {
        int progress = 40 + (int) Math.floor((current - 1) * 45D / Math.max(total, 1));
        JSONObject detail = new JSONObject();
        detail.put("current", current);
        detail.put("total", total);
        updateTaskProgress(
                taskId,
                tenantId,
                documentId,
                stage,
                progress,
                action + "：" + current + "/" + total,
                detail,
                startMillis);
    }

    private void appendChunkEvent(
            Long taskId,
            Long tenantId,
            Long documentId,
            KnowledgeChunkEntity chunk,
            int current,
            int total,
            long startMillis) {
        JSONObject detail = new JSONObject();
        detail.put("current", current);
        detail.put("total", total);
        detail.put("chunkId", String.valueOf(chunk.getId()));
        detail.put("tokenEstimate", chunk.getTokenEstimate());
        detail.put("esIndexed", chunk.isEsIndexed());
        detail.put("milvusIndexed", chunk.isMilvusIndexed());
        appendEvent(
                taskId,
                tenantId,
                documentId,
                "分片完成",
                EVENT_SUCCESS,
                "分片处理完成：" + current + "/" + total,
                detail,
                startMillis);
    }

    private void markTaskSuperseded(
            Long taskId,
            Long tenantId,
            KnowledgeDocumentEntity document,
            KnowledgeIngestResponse response,
            long startMillis) {
        LocalDateTime now = DateTimes.now();
        KnowledgeIngestTaskEntity task = new KnowledgeIngestTaskEntity();
        task.setId(taskId);
        task.setStatus(TASK_SKIPPED);
        task.setStage("版本已过期");
        task.setProgress(100);
        task.setMessage(response.getMessage());
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        ingestTaskMapper.updateById(task);
        appendEvent(
                taskId,
                tenantId,
                document.getId(),
                "版本已过期",
                EVENT_SKIPPED,
                task.getMessage(),
                null,
                startMillis);
    }

    private void markTaskSuccess(
            Long taskId,
            Long tenantId,
            KnowledgeDocumentEntity document,
            KnowledgeIngestResponse response,
            long startMillis) {
        LocalDateTime now = DateTimes.now();
        KnowledgeIngestTaskEntity task = new KnowledgeIngestTaskEntity();
        task.setId(taskId);
        task.setStatus(TASK_SUCCESS);
        task.setStage("完成");
        task.setProgress(100);
        task.setMessage(response.getMessage());
        task.setIndexVersion(response.getIndexVersion());
        task.setIndexHash(response.getIndexHash());
        task.setChunkCount(response.getChunkCount());
        task.setVectorDimension(response.getVectorDimension());
        task.setEmbeddingModel(response.getEmbeddingModel());
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        ingestTaskMapper.updateById(task);
        appendEvent(taskId, tenantId, document.getId(), "完成", EVENT_SUCCESS, response.getMessage(), null, startMillis);
    }

    private void markTaskFailed(
            Long taskId,
            Long tenantId,
            Long documentId,
            Long documentVersionId,
            Long indexGenerationId,
            RuntimeException ex,
            long startMillis) {
        log.warn("知识入库任务执行失败，taskId={}，documentId={}", taskId, documentId, ex);
        LocalDateTime now = DateTimes.now();
        try {
            KnowledgeDocumentEntity document = findDocument(tenantId, documentId);
            if (documentVersionId != null) {
                KnowledgeDocumentVersionEntity version = findVersion(tenantId, documentId, documentVersionId);
                updateVersionStatus(version, KnowledgeDocumentVersionStatus.FAILED, shrink(ex.getMessage(), 500));
                KnowledgeIndexGenerationEntity generation = indexGenerationId == null
                        ? knowledgeIndexGenerationService.requireActive(tenantId)
                        : knowledgeIndexGenerationService.requireGeneration(tenantId, indexGenerationId);
                cleanupVersionIndex(tenantId, documentId, documentVersionId, generation);
            }
            if (documentVersionId != null && documentVersionId.equals(document.getPendingVersionId())) {
                document.setPendingVersionId(null);
            }
            if (document.getActiveVersionId() == null) {
                document.setStatus(KnowledgeDocumentStatus.FAILED.name());
                document.setVectorStatus(KnowledgeVectorStatus.FAILED.name());
            }
            document.setErrorMessage(shrink(ex.getMessage(), 500));
            document.setUpdatedAt(now);
            documentMapper.updateById(document);
        } catch (RuntimeException ignore) {
            log.warn("知识入库失败后更新文档状态失败，documentId={}", documentId, ignore);
        }
        KnowledgeIngestTaskEntity task = new KnowledgeIngestTaskEntity();
        task.setId(taskId);
        task.setStatus(TASK_FAILED);
        task.setStage("失败");
        task.setProgress(100);
        task.setMessage("知识入库失败");
        task.setErrorMessage(shrink(ex.getMessage(), 1000));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        ingestTaskMapper.updateById(task);
        appendEvent(taskId, tenantId, documentId, "失败", EVENT_FAILED, task.getErrorMessage(), null, startMillis);
    }

    private void appendEvent(
            Long taskId,
            Long tenantId,
            Long documentId,
            String stage,
            String status,
            String message,
            JSONObject detail,
            Long startMillis) {
        KnowledgeIngestEventEntity event = new KnowledgeIngestEventEntity();
        event.setId(snowflakeIdGenerator.nextId());
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setDocumentId(documentId);
        event.setStage(stage);
        event.setStatus(status);
        event.setMessage(shrink(message, 500));
        event.setDetailJson(detail == null ? null : detail.toJSONString());
        event.setElapsedMs(startMillis == null ? null : Long.valueOf(System.currentTimeMillis() - startMillis));
        event.setCreatedAt(DateTimes.now());
        ingestEventMapper.insert(event);
        if (EVENT_FAILED.equals(status)) {
            log.warn("知识入库流程失败：taskId={}，documentId={}，stage={}，message={}",
                    taskId, documentId, stage, message);
            return;
        }
        log.info("知识入库流程：taskId={}，documentId={}，stage={}，status={}，message={}",
                taskId, documentId, stage, status, message);
    }

    private QueryWrapper<KnowledgeDocumentEntity> buildDocumentWrapper(
            Long tenantId, KnowledgeDocumentQuery query) {
        QueryWrapper<KnowledgeDocumentEntity> wrapper = new QueryWrapper<KnowledgeDocumentEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq("status", query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq("source_type", query.getSourceType().trim());
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq("category", query.getCategory().trim());
        }
        String keyword = likeKeyword(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .apply("lower(coalesce(title, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(category, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(tags, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(content, '')) like {0}", keyword));
        }
        return wrapper;
    }

    private KnowledgeDocumentEntity findDocument(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("KB_004", "知识文档编号不能为空");
        }
        QueryWrapper<KnowledgeDocumentEntity> wrapper = new QueryWrapper<KnowledgeDocumentEntity>();
        wrapper.eq("id", id);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        KnowledgeDocumentEntity entity = documentMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("KB_005", "知识文档不存在");
        }
        return entity;
    }

    private KnowledgeDocumentEntity findDocumentBySourceKey(Long tenantId, String sourceKey) {
        if (!StringUtils.hasText(sourceKey)) {
            return null;
        }
        return documentMapper.selectOne(
                Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                        .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                        .eq(KnowledgeDocumentEntity::getSourceKey, sourceKey)
                        .eq(KnowledgeDocumentEntity::isDeleted, false)
                        .last("limit 1"));
    }

    private boolean hasSourceIdentity(KnowledgeDocumentRequest request) {
        return request != null
                && (StringUtils.hasText(request.getSourceKey())
                        || StringUtils.hasText(request.getSourceUrl())
                        || StringUtils.hasText(request.getObjectKey()));
    }

    private void validateSourceKeyOwner(Long tenantId, Long documentId, String sourceKey) {
        KnowledgeDocumentEntity owner = findDocumentBySourceKey(tenantId, sourceKey);
        if (owner != null && !documentId.equals(owner.getId())) {
            throw new BusinessException("KB_SOURCE_001", "同一来源已存在知识文档");
        }
    }

    private KnowledgeIngestTaskEntity findTask(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("KB_TASK_001", "知识入库任务编号不能为空");
        }
        QueryWrapper<KnowledgeIngestTaskEntity> wrapper = new QueryWrapper<KnowledgeIngestTaskEntity>();
        wrapper.eq("id", id);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        KnowledgeIngestTaskEntity task = ingestTaskMapper.selectOne(wrapper);
        if (task == null) {
            throw new BusinessException("KB_TASK_002", "知识入库任务不存在");
        }
        return task;
    }

    private Long findTaskVersionId(Long tenantId, Long taskId) {
        try {
            return findTask(tenantId, taskId).getDocumentVersionId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Long findTaskGenerationId(Long tenantId, Long taskId) {
        try {
            return findTask(tenantId, taskId).getIndexGenerationId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private KnowledgeDocumentVersionEntity findVersion(
            Long tenantId, Long documentId, Long versionId) {
        if (versionId == null) {
            throw new BusinessException("KB_VERSION_001", "知识文档版本不能为空");
        }
        KnowledgeDocumentVersionEntity version = documentVersionMapper.selectOne(
                Wrappers.<KnowledgeDocumentVersionEntity>lambdaQuery()
                        .eq(KnowledgeDocumentVersionEntity::getTenantId, tenantId)
                        .eq(KnowledgeDocumentVersionEntity::getDocumentId, documentId)
                        .eq(KnowledgeDocumentVersionEntity::getId, versionId)
                        .last("limit 1"));
        if (version == null) {
            throw new BusinessException("KB_VERSION_002", "知识文档版本不存在");
        }
        return version;
    }

    private void updateVersionStatus(
            KnowledgeDocumentVersionEntity version,
            KnowledgeDocumentVersionStatus status,
            String errorMessage) {
        version.setStatus(status.name());
        version.setErrorMessage(errorMessage);
        version.setUpdatedAt(DateTimes.now());
        documentVersionMapper.updateById(version);
    }

    private KnowledgeChunkEntity buildChunk(
            Long tenantId,
            KnowledgeDocumentEntity document,
            KnowledgeDocumentVersionEntity version,
            KnowledgeIndexGenerationEntity generation,
            KnowledgeTextChunk textChunk,
            int indexVersion,
            String indexHash) {
        LocalDateTime now = DateTimes.now();
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(snowflakeIdGenerator.nextId());
        chunk.setTenantId(tenantId);
        chunk.setDocumentId(document.getId());
        chunk.setDocumentVersionId(version.getId());
        chunk.setIndexGenerationId(generation.getId());
        chunk.setChunkIndex(textChunk.getChunkIndex());
        chunk.setTitle(version.getTitle());
        chunk.setSourceType(version.getSourceType());
        chunk.setCategory(version.getCategory());
        chunk.setTags(version.getTags());
        chunk.setSourceUrl(version.getSourceUrl());
        chunk.setContent(textChunk.getContent());
        chunk.setContentHash(knowledgeFingerprintService.normalizedContentHash(textChunk.getContent()));
        chunk.setIndexHash(indexHash);
        chunk.setIndexVersion(indexVersion);
        chunk.setTokenEstimate(textChunk.getTokenEstimate());
        chunk.setVectorStatus(KnowledgeVectorStatus.WAITING.name());
        chunk.setEsIndexed(false);
        chunk.setMilvusIndexed(false);
        chunk.setMetadataJson(buildMetadataJson(
                document, version, generation, textChunk, indexVersion, indexHash));
        chunk.setCreatedAt(now);
        chunk.setUpdatedAt(now);
        return chunk;
    }

    private String buildMetadataJson(
            KnowledgeDocumentEntity document,
            KnowledgeDocumentVersionEntity version,
            KnowledgeIndexGenerationEntity generation,
            KnowledgeTextChunk textChunk,
            int indexVersion,
            String indexHash) {
        JSONObject metadata = new JSONObject();
        metadata.put("documentId", String.valueOf(document.getId()));
        metadata.put("documentVersionId", String.valueOf(version.getId()));
        metadata.put("indexGenerationId", String.valueOf(generation.getId()));
        metadata.put("title", version.getTitle());
        metadata.put("category", version.getCategory());
        metadata.put("sourceType", version.getSourceType());
        metadata.put("sourceUrl", version.getSourceUrl());
        metadata.put("chunkIndex", textChunk.getChunkIndex());
        metadata.put("indexVersion", indexVersion);
        metadata.put("indexHash", indexHash);
        return metadata.toJSONString();
    }

    private void cleanupRetiredVersions(
            Long tenantId,
            Long documentId,
            Long activeVersionId,
            KnowledgeIndexGenerationEntity generation) {
        QueryWrapper<KnowledgeChunkEntity> queryWrapper = new QueryWrapper<KnowledgeChunkEntity>();
        queryWrapper.eq("tenant_id", tenantId);
        queryWrapper.eq("document_id", documentId);
        queryWrapper.eq("index_generation_id", generation.getId());
        queryWrapper.ne("document_version_id", activeVersionId);
        queryWrapper.eq("deleted", false);
        List<KnowledgeChunkEntity> oldChunks = chunkMapper.selectList(queryWrapper);
        List<Long> versionIds = new ArrayList<Long>();
        for (KnowledgeChunkEntity chunk : oldChunks) {
            if (!versionIds.contains(chunk.getDocumentVersionId())) {
                versionIds.add(chunk.getDocumentVersionId());
            }
        }
        for (Long versionId : versionIds) {
            cleanupVersionIndex(tenantId, documentId, versionId, generation);
        }
    }

    private void scheduleRetiredVersionCleanup(
            Long tenantId,
            Long documentId,
            Long activeVersionId,
            KnowledgeIndexGenerationEntity generation) {
        try {
            knowledgeIngestTaskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    cleanupRetiredVersions(
                            tenantId, documentId, activeVersionId, generation);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("知识旧版本异步清理任务提交失败，documentId={}", documentId, ex);
        }
    }

    private void cleanupVersionIndex(
            Long tenantId,
            Long documentId,
            Long documentVersionId,
            KnowledgeIndexGenerationEntity generation) {
        UpdateWrapper<KnowledgeChunkEntity> wrapper = new UpdateWrapper<KnowledgeChunkEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("document_id", documentId);
        wrapper.eq("document_version_id", documentVersionId);
        wrapper.eq("index_generation_id", generation.getId());
        wrapper.set("deleted", true);
        wrapper.set("updated_at", DateTimes.now());
        chunkMapper.update(null, wrapper);
        try {
            knowledgeElasticsearchClient.deleteByDocumentVersion(
                    tenantId, documentId, documentVersionId, generation.getElasticsearchIndex());
        } catch (RuntimeException ex) {
            log.warn("知识版本分片清理ES失败，documentId={}，versionId={}", documentId, documentVersionId, ex);
        }
        try {
            knowledgeMilvusClient.deleteByDocumentVersion(
                    documentId, documentVersionId, generation.getMilvusCollection());
        } catch (RuntimeException ex) {
            log.warn("知识版本分片清理Milvus失败，documentId={}，versionId={}", documentId, documentVersionId, ex);
        }
    }

    private void cleanupGenerationDocument(
            Long tenantId,
            Long documentId,
            KnowledgeIndexGenerationEntity generation) {
        UpdateWrapper<KnowledgeChunkEntity> wrapper = new UpdateWrapper<KnowledgeChunkEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("document_id", documentId);
        wrapper.eq("index_generation_id", generation.getId());
        wrapper.eq("deleted", false);
        wrapper.set("deleted", true);
        wrapper.set("updated_at", DateTimes.now());
        chunkMapper.update(null, wrapper);
        try {
            knowledgeElasticsearchClient.deleteByDocumentId(
                    tenantId, documentId, generation.getElasticsearchIndex());
        } catch (RuntimeException ex) {
            log.warn("索引代次清理ES文档失败，documentId={}，generationId={}",
                    documentId, generation.getId(), ex);
        }
        try {
            knowledgeMilvusClient.deleteByDocumentId(
                    documentId, generation.getMilvusCollection());
        } catch (RuntimeException ex) {
            log.warn("索引代次清理Milvus文档失败，documentId={}，generationId={}",
                    documentId, generation.getId(), ex);
        }
    }

    private void cleanupExternalIndex(Long tenantId, Long documentId) {
        KnowledgeIndexGenerationEntity generation = knowledgeIndexGenerationService.findActive(tenantId);
        if (generation == null) {
            return;
        }
        try {
            knowledgeElasticsearchClient.deleteByDocumentId(
                    tenantId, documentId, generation.getElasticsearchIndex());
        } catch (RuntimeException ex) {
            log.warn("知识分片清理ES失败，documentId={}", documentId, ex);
        }
        try {
            knowledgeMilvusClient.deleteByDocumentId(documentId, generation.getMilvusCollection());
        } catch (RuntimeException ex) {
            log.warn("知识分片清理Milvus失败，documentId={}", documentId, ex);
        }
    }

    private SearchTune resolveSearchTune(KnowledgeSearchRequest request, int topK) {
        SearchTune tune = new SearchTune();
        tune.vectorCandidates = hybridSearchProperties.safeCandidates(
                request.getVectorCandidates(), hybridSearchProperties.vectorCandidates(), topK);
        tune.keywordCandidates = hybridSearchProperties.safeCandidates(
                request.getKeywordCandidates(), hybridSearchProperties.keywordCandidates(), topK);
        tune.databaseCandidates = hybridSearchProperties.safeCandidates(
                request.getDatabaseCandidates(), hybridSearchProperties.databaseCandidates(), topK);
        tune.vectorWeight =
                hybridSearchProperties.safeWeight(request.getVectorWeight(), hybridSearchProperties.vectorWeight());
        tune.keywordWeight =
                hybridSearchProperties.safeWeight(request.getKeywordWeight(), hybridSearchProperties.keywordWeight());
        tune.databaseWeight =
                hybridSearchProperties.safeWeight(request.getDatabaseWeight(), hybridSearchProperties.databaseWeight());
        tune.minScore = hybridSearchProperties.safeMinScore(request.getMinScore(), hybridSearchProperties.minScore());
        tune.databaseFallbackOnly = request.getDatabaseFallbackOnly() == null
                ? hybridSearchProperties.databaseFallbackOnly()
                : request.getDatabaseFallbackOnly().booleanValue();
        tune.rrfK = hybridSearchProperties.rrfK();
        if (tune.vectorWeight + tune.keywordWeight + tune.databaseWeight <= 0D) {
            tune.vectorWeight = 0.55D;
            tune.keywordWeight = 0.35D;
            tune.databaseWeight = 0.10D;
        }
        return tune;
    }

    private List<KnowledgeSearchHit> collectMilvusHits(
            Long tenantId,
            KnowledgeIndexGenerationEntity generation,
            KnowledgeSearchRequest request,
            int candidateCount) {
        List<KnowledgeSearchHit> result = new ArrayList<KnowledgeSearchHit>();
        if (!knowledgeEmbeddingClient.enabled() || !knowledgeMilvusClient.enabled()) {
            return result;
        }
        List<KnowledgeSearchHit> hits;
        try {
            List<Float> embedding = knowledgeEmbeddingClient.embed(request.getQuery().trim());
            hits = knowledgeMilvusClient.search(
                    generation.getMilvusCollection(), tenantId, embedding, candidateCount);
        } catch (RuntimeException ex) {
            log.info("知识库向量检索降级，tenantId={}，原因={}", tenantId, shrink(ex.getMessage(), 300));
            return result;
        }
        for (KnowledgeSearchHit hit : hits) {
            if (!matchFilter(hit, request)) {
                continue;
            }
            KnowledgeSearchHit filledHit = fillHitFromDatabase(tenantId, generation.getId(), hit);
            if (filledHit != null) {
                result.add(filledHit);
            }
        }
        return result;
    }

    private List<KnowledgeSearchHit> collectElasticsearchHits(
            Long tenantId,
            KnowledgeIndexGenerationEntity generation,
            KnowledgeSearchRequest request,
            int candidateCount) {
        List<KnowledgeSearchHit> result = new ArrayList<KnowledgeSearchHit>();
        if (!knowledgeElasticsearchClient.enabled()) {
            return result;
        }
        List<KnowledgeSearchHit> hits;
        try {
            hits = knowledgeElasticsearchClient.search(
                    generation.getElasticsearchIndex(),
                    tenantId,
                    request.getQuery(),
                    request.getCategory(),
                    request.getSourceType(),
                    candidateCount);
        } catch (RuntimeException ex) {
            log.warn("知识库ES检索失败，tenantId={}", tenantId, ex);
            return result;
        }
        for (KnowledgeSearchHit hit : hits) {
            KnowledgeSearchHit filledHit = fillHitFromDatabase(tenantId, generation.getId(), hit);
            if (filledHit != null) {
                result.add(filledHit);
            }
        }
        return result;
    }

    private List<KnowledgeSearchHit> collectDatabaseHits(
            Long tenantId,
            KnowledgeIndexGenerationEntity generation,
            KnowledgeSearchRequest request,
            int candidateCount) {
        List<KnowledgeSearchHit> result = new ArrayList<KnowledgeSearchHit>();
        QueryWrapper<KnowledgeChunkEntity> wrapper = new QueryWrapper<KnowledgeChunkEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("index_generation_id", generation.getId());
        wrapper.eq("deleted", false);
        if (StringUtils.hasText(request.getCategory())) {
            wrapper.eq("category", request.getCategory().trim());
        }
        if (StringUtils.hasText(request.getSourceType())) {
            wrapper.eq("source_type", request.getSourceType().trim());
        }
        String keyword = likeKeyword(request.getQuery());
        wrapper.and(value -> value
                .apply("lower(coalesce(title, '')) like {0}", keyword)
                .or()
                .apply("lower(coalesce(content, '')) like {0}", keyword)
                .or()
                .apply("lower(coalesce(tags, '')) like {0}", keyword));
        wrapper.orderByDesc("updated_at").last("limit " + candidateCount);
        List<KnowledgeChunkEntity> chunks = chunkMapper.selectList(wrapper);
        for (KnowledgeChunkEntity chunk : chunks) {
            if (isActiveChunk(tenantId, generation.getId(), chunk)) {
                result.add(toHit(chunk, "PG", 0D));
            }
        }
        return result;
    }

    private KnowledgeSearchHit fillHitFromDatabase(
            Long tenantId, Long generationId, KnowledgeSearchHit hit) {
        if (hit == null || !StringUtils.hasText(hit.getChunkId())) {
            return hit;
        }
        Long chunkId = parseLong(hit.getChunkId());
        if (chunkId == null) {
            return hit;
        }
        QueryWrapper<KnowledgeChunkEntity> wrapper = new QueryWrapper<KnowledgeChunkEntity>();
        wrapper.eq("id", chunkId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        KnowledgeChunkEntity chunk = chunkMapper.selectOne(wrapper);
        if (chunk == null) {
            return null;
        }
        if (!isActiveChunk(tenantId, generationId, chunk)) {
            return null;
        }
        return toHit(chunk, hit.getMatchType(), hit.getScore());
    }

    private boolean isActiveChunk(Long tenantId, Long generationId, KnowledgeChunkEntity chunk) {
        if (chunk == null || !generationId.equals(chunk.getIndexGenerationId())) {
            return false;
        }
        KnowledgeDocumentEntity document = documentMapper.selectOne(
                Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                        .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                        .eq(KnowledgeDocumentEntity::getId, chunk.getDocumentId())
                        .eq(KnowledgeDocumentEntity::isDeleted, false)
                        .last("limit 1"));
        return document != null
                && document.getActiveVersionId() != null
                && document.getActiveVersionId().equals(chunk.getDocumentVersionId());
    }

    private KnowledgeSearchHit toHit(KnowledgeChunkEntity chunk, String matchType, Double score) {
        KnowledgeSearchHit hit = new KnowledgeSearchHit();
        hit.setChunkId(String.valueOf(chunk.getId()));
        hit.setDocumentId(String.valueOf(chunk.getDocumentId()));
        hit.setTitle(chunk.getTitle());
        hit.setCategory(chunk.getCategory());
        hit.setSourceType(chunk.getSourceType());
        hit.setSourceUrl(chunk.getSourceUrl());
        hit.setContent(chunk.getContent());
        hit.setIndexVersion(chunk.getIndexVersion());
        hit.setScore(score);
        hit.setMatchType(matchType);
        return hit;
    }

    private List<KnowledgeSearchHit> mergeHybridHits(
            List<KnowledgeSearchHit> vectorHits,
            List<KnowledgeSearchHit> keywordHits,
            List<KnowledgeSearchHit> databaseHits,
            SearchTune tune,
            int topK) {
        LinkedHashMap<String, HybridHitAccumulator> merged =
                new LinkedHashMap<String, HybridHitAccumulator>();
        addHybridChannel(merged, vectorHits, "向量", tune.vectorWeight, tune.rrfK);
        addHybridChannel(merged, keywordHits, "关键词", tune.keywordWeight, tune.rrfK);
        addHybridChannel(merged, databaseHits, "数据库", tune.databaseWeight, tune.rrfK);
        List<KnowledgeSearchHit> hits = new ArrayList<KnowledgeSearchHit>();
        double maxScore = maxHybridScore(vectorHits, keywordHits, databaseHits, tune);
        for (HybridHitAccumulator accumulator : merged.values()) {
            KnowledgeSearchHit hit = accumulator.toHit(maxScore);
            if (hit.getHybridScore() != null && hit.getHybridScore().doubleValue() < tune.minScore) {
                continue;
            }
            hits.add(hit);
        }
        Collections.sort(hits, new Comparator<KnowledgeSearchHit>() {
            @Override
            public int compare(KnowledgeSearchHit first, KnowledgeSearchHit second) {
                return Double.compare(scoreValue(second.getHybridScore()), scoreValue(first.getHybridScore()));
            }
        });
        List<KnowledgeSearchHit> limitedHits = new ArrayList<KnowledgeSearchHit>();
        for (KnowledgeSearchHit hit : hits) {
            if (limitedHits.size() >= topK) {
                break;
            }
            limitedHits.add(hit);
        }
        return limitedHits;
    }

    private void addHybridChannel(
            LinkedHashMap<String, HybridHitAccumulator> merged,
            List<KnowledgeSearchHit> hits,
            String channel,
            double weight,
            int rrfK) {
        if (hits == null || hits.isEmpty() || weight <= 0D) {
            return;
        }
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeSearchHit hit = hits.get(i);
            if (hit == null || !StringUtils.hasText(hit.getChunkId())) {
                continue;
            }
            HybridHitAccumulator accumulator = merged.get(hit.getChunkId());
            if (accumulator == null) {
                accumulator = new HybridHitAccumulator();
                merged.put(hit.getChunkId(), accumulator);
            }
            double rankScore = weight / (rrfK + i + 1D);
            accumulator.add(channel, hit, rankScore);
        }
    }

    private double maxHybridScore(
            List<KnowledgeSearchHit> vectorHits,
            List<KnowledgeSearchHit> keywordHits,
            List<KnowledgeSearchHit> databaseHits,
            SearchTune tune) {
        double activeWeight = 0D;
        if (vectorHits != null && !vectorHits.isEmpty()) {
            activeWeight += tune.vectorWeight;
        }
        if (keywordHits != null && !keywordHits.isEmpty()) {
            activeWeight += tune.keywordWeight;
        }
        if (databaseHits != null && !databaseHits.isEmpty()) {
            activeWeight += tune.databaseWeight;
        }
        if (activeWeight <= 0D) {
            return 1D;
        }
        return activeWeight / (tune.rrfK + 1D);
    }

    private static double scoreValue(Double score) {
        return score == null ? 0D : score.doubleValue();
    }

    private boolean matchFilter(KnowledgeSearchHit hit, KnowledgeSearchRequest request) {
        if (hit == null) {
            return false;
        }
        if (StringUtils.hasText(request.getCategory())
                && !request.getCategory().trim().equals(hit.getCategory())) {
            return false;
        }
        return !StringUtils.hasText(request.getSourceType())
                || request.getSourceType().trim().equals(hit.getSourceType());
    }

    private boolean shouldBuildVector() {
        return knowledgeEmbeddingClient.enabled() && knowledgeMilvusClient.enabled();
    }

    private KnowledgeIngestResponse toIngestResponse(KnowledgeDocumentEntity document) {
        KnowledgeIngestResponse response = new KnowledgeIngestResponse();
        response.setDocumentId(String.valueOf(document.getId()));
        response.setAsyncTask(false);
        response.setStatus(document.getStatus());
        response.setVectorStatus(document.getVectorStatus());
        response.setChunkCount(document.getChunkCount());
        response.setVectorDimension(document.getVectorDimension());
        response.setEmbeddingModel(document.getEmbeddingModel());
        response.setIndexVersion(document.getIndexVersion());
        response.setIndexHash(document.getIndexHash());
        return response;
    }

    private KnowledgeIngestTaskResponse toTaskResponse(
            KnowledgeIngestTaskEntity task, List<KnowledgeIngestEventEntity> events) {
        KnowledgeIngestTaskResponse response = new KnowledgeIngestTaskResponse();
        response.setId(String.valueOf(task.getId()));
        response.setDocumentId(String.valueOf(task.getDocumentId()));
        response.setDocumentVersionId(
                task.getDocumentVersionId() == null ? null : String.valueOf(task.getDocumentVersionId()));
        response.setIndexGenerationId(
                task.getIndexGenerationId() == null ? null : String.valueOf(task.getIndexGenerationId()));
        response.setForce(Boolean.valueOf(Boolean.TRUE.equals(task.getForce())));
        response.setStatus(task.getStatus());
        response.setStage(task.getStage());
        response.setProgress(task.getProgress());
        response.setMessage(task.getMessage());
        response.setErrorMessage(task.getErrorMessage());
        response.setIndexVersion(task.getIndexVersion());
        response.setIndexHash(task.getIndexHash());
        response.setChunkCount(task.getChunkCount());
        response.setVectorDimension(task.getVectorDimension());
        response.setEmbeddingModel(task.getEmbeddingModel());
        response.setStartedAt(task.getStartedAt());
        response.setFinishedAt(task.getFinishedAt());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        List<KnowledgeIngestEventResponse> eventResponses = new ArrayList<KnowledgeIngestEventResponse>();
        if (events != null) {
            for (KnowledgeIngestEventEntity event : events) {
                eventResponses.add(toEventResponse(event));
            }
        }
        response.setEvents(eventResponses);
        return response;
    }

    private KnowledgeIngestEventResponse toEventResponse(KnowledgeIngestEventEntity event) {
        KnowledgeIngestEventResponse response = new KnowledgeIngestEventResponse();
        response.setId(String.valueOf(event.getId()));
        response.setTaskId(String.valueOf(event.getTaskId()));
        response.setDocumentId(String.valueOf(event.getDocumentId()));
        response.setStage(event.getStage());
        response.setStatus(event.getStatus());
        response.setMessage(event.getMessage());
        response.setDetailJson(event.getDetailJson());
        response.setElapsedMs(event.getElapsedMs());
        response.setCreatedAt(event.getCreatedAt());
        return response;
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocumentEntity entity, boolean includeContent) {
        KnowledgeDocumentResponse response = new KnowledgeDocumentResponse();
        response.setId(String.valueOf(entity.getId()));
        response.setSourceKey(entity.getSourceKey());
        response.setRawFileHash(entity.getRawFileHash());
        response.setNormalizedContentHash(entity.getNormalizedContentHash());
        response.setActiveVersionId(
                entity.getActiveVersionId() == null ? null : String.valueOf(entity.getActiveVersionId()));
        response.setPendingVersionId(
                entity.getPendingVersionId() == null ? null : String.valueOf(entity.getPendingVersionId()));
        response.setTitle(entity.getTitle());
        response.setSourceType(entity.getSourceType());
        response.setCategory(entity.getCategory());
        response.setTags(entity.getTags());
        response.setSourceUrl(entity.getSourceUrl());
        response.setObjectKey(entity.getObjectKey());
        response.setContent(includeContent ? entity.getContent() : null);
        response.setStatus(entity.getStatus());
        response.setVectorStatus(entity.getVectorStatus());
        response.setChunkCount(entity.getChunkCount());
        response.setVectorDimension(entity.getVectorDimension());
        response.setEmbeddingModel(entity.getEmbeddingModel());
        response.setIndexVersion(entity.getIndexVersion());
        response.setIndexHash(entity.getIndexHash());
        response.setIndexedAt(entity.getIndexedAt());
        response.setErrorMessage(entity.getErrorMessage());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private String resolveIngestMessage(boolean hasRetrievalIndex, boolean hasVectorIndex) {
        if (hasVectorIndex) {
            return "知识已完成切分、向量化，并写入Milvus和可用检索索引";
        }
        if (hasRetrievalIndex) {
            return "知识已完成切分并写入ES，向量模型或Milvus未启用";
        }
        return "知识已完成切分，暂未配置ES或Milvus索引";
    }

    private String resolveSearchMessage(KnowledgeSearchResponse response) {
        if (!response.getHits().isEmpty()) {
            return "混合检索完成";
        }
        if (!response.isElasticsearchEnabled() && !response.isMilvusEnabled()) {
            return "未启用ES和Milvus，已尝试PG降级检索";
        }
        return "未检索到匹配知识";
    }

    private int safeTopK(Integer value) {
        int topK = value == null ? 5 : value.intValue();
        if (topK < 1) {
            return 1;
        }
        return Math.min(topK, 20);
    }

    private int safeProgress(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 100);
    }

    private String likeKeyword(String value) {
        String keyword = trimToNull(value);
        return keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String shrink(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private static class SearchTune {

        private int vectorCandidates;

        private int keywordCandidates;

        private int databaseCandidates;

        private double vectorWeight;

        private double keywordWeight;

        private double databaseWeight;

        private double minScore;

        private boolean databaseFallbackOnly;

        private int rrfK;
    }

    private static class HybridHitAccumulator {

        private KnowledgeSearchHit hit;

        private double hybridScore;

        private Double vectorScore;

        private Double keywordScore;

        private Double databaseScore;

        private boolean vectorMatched;

        private boolean keywordMatched;

        private boolean databaseMatched;

        private void add(String channel, KnowledgeSearchHit source, double rankScore) {
            if (source == null) {
                return;
            }
            if (hit == null) {
                hit = source;
            } else {
                fillMissing(hit, source);
            }
            hybridScore += rankScore;
            Double rawScore = resolveRawScore(channel, source, rankScore);
            if ("向量".equals(channel)) {
                vectorMatched = true;
                vectorScore = betterScore(vectorScore, rawScore);
            } else if ("关键词".equals(channel)) {
                keywordMatched = true;
                keywordScore = betterScore(keywordScore, rawScore);
            } else {
                databaseMatched = true;
                databaseScore = betterScore(databaseScore, rawScore);
            }
        }

        private KnowledgeSearchHit toHit(double maxScore) {
            if (hit == null) {
                return new KnowledgeSearchHit();
            }
            double normalizedScore = maxScore <= 0D ? 0D : hybridScore / maxScore;
            if (normalizedScore > 1D) {
                normalizedScore = 1D;
            }
            hit.setHybridScore(roundScore(normalizedScore));
            hit.setScore(hit.getHybridScore());
            hit.setVectorScore(roundNullable(vectorScore));
            hit.setKeywordScore(roundNullable(keywordScore));
            hit.setDatabaseScore(roundNullable(databaseScore));
            hit.setMatchChannels(resolveMatchChannels());
            hit.setMatchType(hit.getMatchChannels());
            return hit;
        }

        private String resolveMatchChannels() {
            StringBuilder builder = new StringBuilder();
            appendChannel(builder, vectorMatched, "向量");
            appendChannel(builder, keywordMatched, "关键词");
            appendChannel(builder, databaseMatched, "数据库");
            return builder.toString();
        }

        private static void appendChannel(StringBuilder builder, boolean matched, String channel) {
            if (!matched) {
                return;
            }
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(channel);
        }

        private static void fillMissing(KnowledgeSearchHit target, KnowledgeSearchHit source) {
            if (!StringUtils.hasText(target.getDocumentId())) {
                target.setDocumentId(source.getDocumentId());
            }
            if (!StringUtils.hasText(target.getTitle())) {
                target.setTitle(source.getTitle());
            }
            if (!StringUtils.hasText(target.getCategory())) {
                target.setCategory(source.getCategory());
            }
            if (!StringUtils.hasText(target.getSourceType())) {
                target.setSourceType(source.getSourceType());
            }
            if (!StringUtils.hasText(target.getSourceUrl())) {
                target.setSourceUrl(source.getSourceUrl());
            }
            if (!StringUtils.hasText(target.getContent())) {
                target.setContent(source.getContent());
            }
            if (target.getIndexVersion() == null) {
                target.setIndexVersion(source.getIndexVersion());
            }
        }

        private static Double resolveRawScore(String channel, KnowledgeSearchHit source, double rankScore) {
            Double rawScore = source.getScore();
            if (rawScore == null || ("数据库".equals(channel) && rawScore.doubleValue() == 0D)) {
                return Double.valueOf(rankScore);
            }
            return rawScore;
        }

        private static Double betterScore(Double oldScore, Double newScore) {
            if (newScore == null) {
                return oldScore;
            }
            if (oldScore == null) {
                return newScore;
            }
            return Double.valueOf(Math.max(oldScore.doubleValue(), newScore.doubleValue()));
        }

        private static Double roundNullable(Double value) {
            if (value == null) {
                return null;
            }
            return Double.valueOf(roundScore(value.doubleValue()));
        }

        private static double roundScore(double value) {
            return Math.round(value * 10000D) / 10000D;
        }
    }
}
