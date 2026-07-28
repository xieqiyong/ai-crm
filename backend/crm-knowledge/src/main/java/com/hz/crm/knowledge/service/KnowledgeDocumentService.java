package com.hz.crm.knowledge.service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import com.hz.crm.knowledge.mapper.KnowledgeIngestEventMapper;
import com.hz.crm.knowledge.mapper.KnowledgeIngestTaskMapper;
import com.hz.crm.knowledge.support.KnowledgeTextChunk;
import com.hz.crm.knowledge.support.KnowledgeTextSplitter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    private static final String TASK_PENDING = "PENDING";

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
        String oldIndexHash = null;
        LocalDateTime now = DateTimes.now();
        if (request.getId() == null) {
            entity = new KnowledgeDocumentEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setStatus(KnowledgeDocumentStatus.DRAFT.name());
            entity.setVectorStatus(KnowledgeVectorStatus.WAITING.name());
            entity.setChunkCount(0);
            entity.setIndexVersion(0);
            entity.setCreatedAt(now);
        } else {
            entity = findDocument(tenantId, request.getId());
            oldIndexHash = entity.getIndexHash();
        }
        entity.setUpdatedAt(now);
        entity.setTitle(request.getTitle().trim());
        entity.setSourceType(trimToNull(request.getSourceType()));
        entity.setCategory(trimToNull(request.getCategory()));
        entity.setTags(trimToNull(request.getTags()));
        entity.setSourceUrl(trimToNull(request.getSourceUrl()));
        entity.setObjectKey(trimToNull(request.getObjectKey()));
        entity.setContent(trimToNull(request.getContent()));
        entity.setErrorMessage(null);
        markWaitingIfIndexChanged(entity, oldIndexHash);
        if (request.getId() == null) {
            documentMapper.insert(entity);
        } else {
            documentMapper.updateById(entity);
        }
        return toResponse(entity, true);
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        KnowledgeDocumentEntity entity = findDocument(tenantId, id);
        LocalDateTime now = DateTimes.now();
        entity.setDeleted(true);
        entity.setUpdatedAt(now);
        documentMapper.updateById(entity);
        UpdateWrapper<KnowledgeChunkEntity> wrapper = new UpdateWrapper<KnowledgeChunkEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("document_id", id);
        wrapper.set("deleted", true);
        wrapper.set("updated_at", now);
        chunkMapper.update(null, wrapper);
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
        KnowledgeDocumentEntity document = findDocument(tenantId, id);
        if (!StringUtils.hasText(document.getContent())) {
            throw new BusinessException("KB_002", "知识内容不能为空");
        }
        KnowledgeIngestTaskEntity task = createIngestTask(tenantId, document, force);
        try {
            knowledgeIngestTaskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    runIngestTask(task.getId(), tenantId, id, force);
                }
            });
        } catch (RuntimeException ex) {
            failTaskSubmit(task, ex);
        }
        KnowledgeIngestResponse response = toIngestResponse(document);
        response.setTaskId(String.valueOf(task.getId()));
        response.setAsyncTask(true);
        response.setStatus(task.getStatus());
        response.setStage(task.getStage());
        response.setProgress(task.getProgress());
        response.setSkipped(false);
        response.setMessage(task.getMessage());
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

        List<KnowledgeSearchHit> vectorHits = collectMilvusHits(tenantId, request, tune.vectorCandidates);
        List<KnowledgeSearchHit> keywordHits =
                collectElasticsearchHits(tenantId, request, tune.keywordCandidates);
        List<KnowledgeSearchHit> databaseHits = new ArrayList<KnowledgeSearchHit>();
        boolean shouldUseDatabase = !tune.databaseFallbackOnly || (vectorHits.isEmpty() && keywordHits.isEmpty());
        if (shouldUseDatabase) {
            databaseHits = collectDatabaseHits(tenantId, request, tune.databaseCandidates);
        }
        response.setDatabaseFallbackUsed(shouldUseDatabase);
        response.setHits(mergeHybridHits(vectorHits, keywordHits, databaseHits, tune, topK));
        response.setMessage(resolveSearchMessage(response));
        return response;
    }

    private KnowledgeIngestTaskEntity createIngestTask(
            Long tenantId, KnowledgeDocumentEntity document, boolean force) {
        LocalDateTime now = DateTimes.now();
        KnowledgeIngestTaskEntity task = new KnowledgeIngestTaskEntity();
        task.setId(snowflakeIdGenerator.nextId());
        task.setTenantId(tenantId);
        task.setDocumentId(document.getId());
        task.setForce(Boolean.valueOf(force));
        task.setStatus(TASK_PENDING);
        task.setStage("排队中");
        task.setProgress(0);
        task.setMessage("知识入库任务已提交，等待后台执行");
        task.setDeleted(Boolean.FALSE);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        ingestTaskMapper.insert(task);
        appendEvent(task.getId(), tenantId, document.getId(), "排队中", EVENT_START, task.getMessage(), null, null);
        return task;
    }

    private void failTaskSubmit(KnowledgeIngestTaskEntity task, RuntimeException ex) {
        log.warn("知识入库任务提交失败，taskId={}，documentId={}", task.getId(), task.getDocumentId(), ex);
        LocalDateTime now = DateTimes.now();
        task.setStatus(TASK_FAILED);
        task.setStage("提交失败");
        task.setProgress(100);
        task.setMessage("知识入库任务提交失败");
        task.setErrorMessage(shrink(ex.getMessage(), 1000));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        ingestTaskMapper.updateById(task);
        appendEvent(
                task.getId(),
                task.getTenantId(),
                task.getDocumentId(),
                "提交失败",
                EVENT_FAILED,
                task.getErrorMessage(),
                null,
                null);
    }

    private void runIngestTask(Long taskId, Long tenantId, Long documentId, boolean force) {
        long startMillis = System.currentTimeMillis();
        markTaskRunning(taskId, tenantId, documentId, startMillis);
        try {
            KnowledgeDocumentEntity document = findDocument(tenantId, documentId);
            updateTaskProgress(taskId, tenantId, documentId, "读取文档", 8, "已读取知识文档", startMillis);
            List<KnowledgeTextChunk> chunks = splitDocument(taskId, tenantId, document, startMillis);
            String indexHash = buildIndexHash(document, chunks);
            if (!force && canSkipIngest(tenantId, document, indexHash)) {
                markTaskSkipped(taskId, tenantId, document, startMillis);
                return;
            }
            prepareDocumentForIngest(taskId, tenantId, document, startMillis);
            KnowledgeIngestResponse response = doIngest(taskId, tenantId, document, chunks, indexHash, startMillis);
            markTaskSuccess(taskId, tenantId, document, response, startMillis);
        } catch (RuntimeException ex) {
            markTaskFailed(taskId, tenantId, documentId, ex, startMillis);
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
            Long taskId, Long tenantId, KnowledgeDocumentEntity document, long startMillis) {
        updateTaskProgress(taskId, tenantId, document.getId(), "文档切分", 15, "开始切分知识文档", startMillis);
        List<KnowledgeTextChunk> chunks = knowledgeTextSplitter.split(document.getContent());
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
            Long taskId, Long tenantId, KnowledgeDocumentEntity document, long startMillis) {
        updateTaskProgress(taskId, tenantId, document.getId(), "准备索引", 30, "开始准备索引重建", startMillis);
        LocalDateTime now = DateTimes.now();
        document.setStatus(KnowledgeDocumentStatus.INDEXING.name());
        document.setVectorStatus(KnowledgeVectorStatus.WAITING.name());
        document.setErrorMessage(null);
        document.setUpdatedAt(now);
        documentMapper.updateById(document);
    }

    private KnowledgeIngestResponse doIngest(
            Long taskId,
            Long tenantId,
            KnowledgeDocumentEntity document,
            List<KnowledgeTextChunk> chunks,
            String indexHash,
            long startMillis) {
        int indexVersion = nextIndexVersion(document);
        updateTaskProgress(taskId, tenantId, document.getId(), "清理旧索引", 35, "开始清理旧分片和外部索引", startMillis);
        cleanupOldChunks(tenantId, document.getId());
        cleanupExternalIndex(tenantId, document.getId());
        boolean hasRetrievalIndex = false;
        boolean hasVectorIndex = false;
        Integer vectorDimension = null;
        int total = chunks.size();
        for (KnowledgeTextChunk textChunk : chunks) {
            int current = textChunk.getChunkIndex().intValue() + 1;
            KnowledgeChunkEntity chunk = buildChunk(tenantId, document, textChunk, indexVersion, indexHash);
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
                knowledgeElasticsearchClient.index(chunk);
                chunk.setEsIndexed(true);
                hasRetrievalIndex = true;
            }
            if (embedding != null) {
                updateChunkProgress(
                        taskId, tenantId, document.getId(), "写入Milvus", current, total, "写入Milvus向量库", startMillis);
                knowledgeMilvusClient.index(chunk, embedding);
                chunk.setMilvusIndexed(true);
                hasRetrievalIndex = true;
                hasVectorIndex = true;
            }
            chunk.setUpdatedAt(DateTimes.now());
            chunkMapper.updateById(chunk);
            appendChunkEvent(taskId, tenantId, document.getId(), chunk, current, total, startMillis);
        }
        fillDocumentIndexedStatus(
                document, chunks.size(), hasRetrievalIndex, hasVectorIndex, vectorDimension, indexVersion, indexHash);
        documentMapper.updateById(document);
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

    private void markTaskSkipped(
            Long taskId, Long tenantId, KnowledgeDocumentEntity document, long startMillis) {
        LocalDateTime now = DateTimes.now();
        KnowledgeIngestTaskEntity task = new KnowledgeIngestTaskEntity();
        task.setId(taskId);
        task.setStatus(TASK_SKIPPED);
        task.setStage("跳过入库");
        task.setProgress(100);
        task.setMessage("知识内容和索引配置未变化，已跳过重复入库");
        task.setIndexVersion(document.getIndexVersion());
        task.setIndexHash(document.getIndexHash());
        task.setChunkCount(document.getChunkCount());
        task.setVectorDimension(document.getVectorDimension());
        task.setEmbeddingModel(document.getEmbeddingModel());
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        ingestTaskMapper.updateById(task);
        appendEvent(
                taskId,
                tenantId,
                document.getId(),
                "跳过入库",
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

    private void markTaskFailed(Long taskId, Long tenantId, Long documentId, RuntimeException ex, long startMillis) {
        log.warn("知识入库任务执行失败，taskId={}，documentId={}", taskId, documentId, ex);
        LocalDateTime now = DateTimes.now();
        try {
            KnowledgeDocumentEntity document = findDocument(tenantId, documentId);
            document.setStatus(KnowledgeDocumentStatus.FAILED.name());
            document.setVectorStatus(KnowledgeVectorStatus.FAILED.name());
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

    private void markWaitingIfIndexChanged(KnowledgeDocumentEntity entity, String oldIndexHash) {
        if (!StringUtils.hasText(oldIndexHash)) {
            return;
        }
        List<KnowledgeTextChunk> chunks = knowledgeTextSplitter.split(entity.getContent());
        String newIndexHash = buildIndexHash(entity, chunks);
        if (oldIndexHash.equals(newIndexHash)) {
            return;
        }
        entity.setStatus(KnowledgeDocumentStatus.DRAFT.name());
        entity.setVectorStatus(KnowledgeVectorStatus.WAITING.name());
        entity.setErrorMessage(null);
    }

    private boolean canSkipIngest(Long tenantId, KnowledgeDocumentEntity document, String indexHash) {
        if (!StringUtils.hasText(document.getIndexHash()) || !document.getIndexHash().equals(indexHash)) {
            return false;
        }
        if (document.getIndexVersion() == null || document.getIndexVersion().intValue() <= 0) {
            return false;
        }
        if (!KnowledgeDocumentStatus.READY.name().equals(document.getStatus())) {
            return false;
        }
        return hasActiveChunks(tenantId, document.getId(), document.getIndexVersion());
    }

    private boolean hasActiveChunks(Long tenantId, Long documentId, Integer indexVersion) {
        QueryWrapper<KnowledgeChunkEntity> wrapper = new QueryWrapper<KnowledgeChunkEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("document_id", documentId);
        wrapper.eq("index_version", indexVersion);
        wrapper.eq("deleted", false);
        Long count = chunkMapper.selectCount(wrapper);
        return count != null && count.longValue() > 0L;
    }

    private int nextIndexVersion(KnowledgeDocumentEntity document) {
        Integer value = document.getIndexVersion();
        if (value == null || value.intValue() < 0) {
            return 1;
        }
        return value.intValue() + 1;
    }

    private String buildIndexHash(KnowledgeDocumentEntity document, List<KnowledgeTextChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        appendHashValue(builder, "title", document.getTitle());
        appendHashValue(builder, "sourceType", document.getSourceType());
        appendHashValue(builder, "category", document.getCategory());
        appendHashValue(builder, "tags", document.getTags());
        appendHashValue(builder, "sourceUrl", document.getSourceUrl());
        appendHashValue(builder, "objectKey", document.getObjectKey());
        appendHashValue(builder, "content", document.getContent());
        appendHashValue(builder, "splitter", knowledgeTextSplitter.profile());
        appendHashValue(builder, "embeddingEnabled", knowledgeEmbeddingClient.enabled());
        appendHashValue(
                builder, "embeddingModel", knowledgeEmbeddingClient.enabled() ? knowledgeEmbeddingClient.model() : "");
        appendHashValue(builder, "embeddingDimensions", knowledgeEmbeddingClient.dimensions());
        appendHashValue(builder, "milvusEnabled", knowledgeMilvusClient.enabled());
        appendHashValue(builder, "elasticsearchEnabled", knowledgeElasticsearchClient.enabled());
        if (chunks != null) {
            for (KnowledgeTextChunk chunk : chunks) {
                appendHashValue(builder, "chunkIndex", chunk.getChunkIndex());
                appendHashValue(builder, "chunkHash", sha256(chunk.getContent()));
            }
        }
        return sha256(builder.toString());
    }

    private void appendHashValue(StringBuilder builder, String key, Object value) {
        builder.append(key);
        builder.append('=');
        builder.append(value == null ? "" : String.valueOf(value));
        builder.append('\n');
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

    private KnowledgeChunkEntity buildChunk(
            Long tenantId,
            KnowledgeDocumentEntity document,
            KnowledgeTextChunk textChunk,
            int indexVersion,
            String indexHash) {
        LocalDateTime now = DateTimes.now();
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(snowflakeIdGenerator.nextId());
        chunk.setTenantId(tenantId);
        chunk.setDocumentId(document.getId());
        chunk.setChunkIndex(textChunk.getChunkIndex());
        chunk.setTitle(document.getTitle());
        chunk.setSourceType(document.getSourceType());
        chunk.setCategory(document.getCategory());
        chunk.setTags(document.getTags());
        chunk.setSourceUrl(document.getSourceUrl());
        chunk.setContent(textChunk.getContent());
        chunk.setContentHash(sha256(textChunk.getContent()));
        chunk.setIndexHash(indexHash);
        chunk.setIndexVersion(indexVersion);
        chunk.setTokenEstimate(textChunk.getTokenEstimate());
        chunk.setVectorStatus(KnowledgeVectorStatus.WAITING.name());
        chunk.setEsIndexed(false);
        chunk.setMilvusIndexed(false);
        chunk.setMetadataJson(buildMetadataJson(document, textChunk, indexVersion, indexHash));
        chunk.setCreatedAt(now);
        chunk.setUpdatedAt(now);
        return chunk;
    }

    private String buildMetadataJson(
            KnowledgeDocumentEntity document, KnowledgeTextChunk textChunk, int indexVersion, String indexHash) {
        JSONObject metadata = new JSONObject();
        metadata.put("documentId", String.valueOf(document.getId()));
        metadata.put("title", document.getTitle());
        metadata.put("category", document.getCategory());
        metadata.put("sourceType", document.getSourceType());
        metadata.put("sourceUrl", document.getSourceUrl());
        metadata.put("chunkIndex", textChunk.getChunkIndex());
        metadata.put("indexVersion", indexVersion);
        metadata.put("indexHash", indexHash);
        return metadata.toJSONString();
    }

    private void cleanupOldChunks(Long tenantId, Long documentId) {
        QueryWrapper<KnowledgeChunkEntity> queryWrapper = new QueryWrapper<KnowledgeChunkEntity>();
        queryWrapper.eq("tenant_id", tenantId);
        queryWrapper.eq("document_id", documentId);
        queryWrapper.eq("deleted", false);
        Long oldCount = chunkMapper.selectCount(queryWrapper);
        if (oldCount == null || oldCount.longValue() == 0L) {
            return;
        }
        UpdateWrapper<KnowledgeChunkEntity> wrapper = new UpdateWrapper<KnowledgeChunkEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("document_id", documentId);
        wrapper.set("deleted", true);
        wrapper.set("updated_at", DateTimes.now());
        chunkMapper.update(null, wrapper);
    }

    private void cleanupExternalIndex(Long tenantId, Long documentId) {
        try {
            knowledgeElasticsearchClient.deleteByDocumentId(tenantId, documentId);
        } catch (RuntimeException ex) {
            log.warn("知识分片清理ES失败，documentId={}", documentId, ex);
        }
        try {
            knowledgeMilvusClient.deleteByDocumentId(documentId);
        } catch (RuntimeException ex) {
            log.warn("知识分片清理Milvus失败，documentId={}", documentId, ex);
        }
    }

    private void fillDocumentIndexedStatus(
            KnowledgeDocumentEntity document,
            int chunkCount,
            boolean hasRetrievalIndex,
            boolean hasVectorIndex,
            Integer vectorDimension,
            int indexVersion,
            String indexHash) {
        document.setChunkCount(chunkCount);
        document.setVectorStatus(
                hasVectorIndex ? KnowledgeVectorStatus.READY.name() : KnowledgeVectorStatus.WAITING.name());
        String status = hasRetrievalIndex
                ? KnowledgeDocumentStatus.READY.name()
                : KnowledgeDocumentStatus.WAITING_VECTOR.name();
        document.setStatus(status);
        document.setVectorDimension(hasVectorIndex ? vectorDimension : null);
        document.setEmbeddingModel(hasVectorIndex ? knowledgeEmbeddingClient.model() : null);
        document.setIndexVersion(indexVersion);
        document.setIndexHash(indexHash);
        document.setIndexedAt(DateTimes.now());
        document.setErrorMessage(null);
        document.setUpdatedAt(DateTimes.now());
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
            Long tenantId, KnowledgeSearchRequest request, int candidateCount) {
        List<KnowledgeSearchHit> result = new ArrayList<KnowledgeSearchHit>();
        if (!knowledgeEmbeddingClient.enabled() || !knowledgeMilvusClient.enabled()) {
            return result;
        }
        List<KnowledgeSearchHit> hits;
        try {
            List<Float> embedding = knowledgeEmbeddingClient.embed(request.getQuery().trim());
            hits = knowledgeMilvusClient.search(tenantId, embedding, candidateCount);
        } catch (RuntimeException ex) {
            log.warn("知识库向量检索失败，tenantId={}", tenantId, ex);
            return result;
        }
        for (KnowledgeSearchHit hit : hits) {
            if (!matchFilter(hit, request)) {
                continue;
            }
            KnowledgeSearchHit filledHit = fillHitFromDatabase(tenantId, hit);
            if (filledHit != null) {
                result.add(filledHit);
            }
        }
        return result;
    }

    private List<KnowledgeSearchHit> collectElasticsearchHits(
            Long tenantId, KnowledgeSearchRequest request, int candidateCount) {
        List<KnowledgeSearchHit> result = new ArrayList<KnowledgeSearchHit>();
        if (!knowledgeElasticsearchClient.enabled()) {
            return result;
        }
        List<KnowledgeSearchHit> hits;
        try {
            hits = knowledgeElasticsearchClient.search(
                    tenantId, request.getQuery(), request.getCategory(), request.getSourceType(), candidateCount);
        } catch (RuntimeException ex) {
            log.warn("知识库ES检索失败，tenantId={}", tenantId, ex);
            return result;
        }
        for (KnowledgeSearchHit hit : hits) {
            if (hit != null && StringUtils.hasText(hit.getChunkId())) {
                result.add(hit);
            }
        }
        return result;
    }

    private List<KnowledgeSearchHit> collectDatabaseHits(
            Long tenantId, KnowledgeSearchRequest request, int candidateCount) {
        List<KnowledgeSearchHit> result = new ArrayList<KnowledgeSearchHit>();
        QueryWrapper<KnowledgeChunkEntity> wrapper = new QueryWrapper<KnowledgeChunkEntity>();
        wrapper.eq("tenant_id", tenantId);
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
            KnowledgeSearchHit hit = toHit(chunk, "PG", 0D);
            result.add(hit);
        }
        return result;
    }

    private KnowledgeSearchHit fillHitFromDatabase(Long tenantId, KnowledgeSearchHit hit) {
        if (hit == null || !StringUtils.hasText(hit.getChunkId())) {
            return hit;
        }
        if (StringUtils.hasText(hit.getContent()) && StringUtils.hasText(hit.getTitle())) {
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
            return hit;
        }
        KnowledgeSearchHit value = toHit(chunk, hit.getMatchType(), hit.getScore());
        return value;
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

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            return null;
        }
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
