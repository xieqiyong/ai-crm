package com.hz.crm.knowledge.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.knowledge.domain.KnowledgeChunkEntity;
import com.hz.crm.knowledge.dto.KnowledgeSearchHit;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeMilvusClient {

    @Value("${crm.knowledge.milvus.enabled:false}")
    private boolean enabled;

    @Value("${crm.knowledge.milvus.endpoint:http://localhost:19530}")
    private String endpoint;

    @Value("${crm.knowledge.milvus.token:}")
    private String token;

    @Value("${crm.knowledge.milvus.database:}")
    private String database;

    @Value("${crm.knowledge.milvus.collection:crm_knowledge_chunk}")
    private String collection;

    @Value("${crm.knowledge.milvus.timeout-ms:10000}")
    private int timeoutMs;

    public boolean enabled() {
        return enabled && StringUtils.hasText(endpoint) && StringUtils.hasText(collection);
    }

    public String generationCollectionName(Long generationId) {
        return safeCollectionName(collection) + "_g_" + generationId;
    }

    public void index(KnowledgeChunkEntity chunk, List<Float> embedding) {
        index(chunk, embedding, collection);
    }

    public void index(
            KnowledgeChunkEntity chunk, List<Float> embedding, String targetCollection) {
        if (!enabled()) {
            return;
        }
        if (embedding == null || embedding.isEmpty()) {
            throw new BusinessException("KB_MILVUS_001", "知识分片向量不能为空");
        }
        try {
            ensureDatabase();
            ensureCollection(targetCollection, embedding.size());
            request("POST", "/v2/vectordb/entities/insert",
                    buildInsertBody(chunk, embedding, targetCollection));
        } catch (Exception ex) {
            throw new BusinessException("KB_MILVUS_002", "知识分片写入Milvus失败：" + ex.getMessage());
        }
    }

    public List<KnowledgeSearchHit> search(Long tenantId, List<Float> embedding, int topK) {
        return search(collection, tenantId, embedding, topK);
    }

    public List<KnowledgeSearchHit> search(
            String targetCollection, Long tenantId, List<Float> embedding, int topK) {
        List<KnowledgeSearchHit> hits = new ArrayList<KnowledgeSearchHit>();
        if (!enabled() || embedding == null || embedding.isEmpty()) {
            return hits;
        }
        try {
            String responseText = request("POST", "/v2/vectordb/entities/search",
                    buildSearchBody(targetCollection, tenantId, embedding, topK));
            JSONObject response = JSON.parseObject(responseText);
            JSONArray data = response.getJSONArray("data");
            if (data == null) {
                return hits;
            }
            fillHits(hits, data);
            return hits;
        } catch (Exception ex) {
            throw new BusinessException("KB_MILVUS_003", "知识库Milvus检索失败：" + ex.getMessage());
        }
    }

    public void deleteByDocumentId(Long documentId) {
        deleteByDocumentId(documentId, collection);
    }

    public void deleteByDocumentId(Long documentId, String targetCollection) {
        if (!enabled() || documentId == null) {
            return;
        }
        try {
            ensureDatabase();
            if (!hasCollection(targetCollection)) {
                return;
            }
            JSONObject body = new JSONObject();
            body.put("collectionName", safeCollectionName(targetCollection));
            body.put("filter", "document_id == \"" + documentId + "\"");
            fillDatabase(body);
            request("POST", "/v2/vectordb/entities/delete", body);
        } catch (Exception ex) {
            throw new BusinessException("KB_MILVUS_005", "知识分片清理Milvus失败：" + ex.getMessage());
        }
    }

    public void deleteByDocumentVersion(
            Long documentId, Long documentVersionId, String targetCollection) {
        if (!enabled() || documentId == null || documentVersionId == null) {
            return;
        }
        try {
            ensureDatabase();
            if (!hasCollection(targetCollection)) {
                return;
            }
            JSONObject body = new JSONObject();
            body.put("collectionName", safeCollectionName(targetCollection));
            body.put("filter", "document_id == \"" + documentId
                    + "\" and document_version_id == \"" + documentVersionId + "\"");
            fillDatabase(body);
            request("POST", "/v2/vectordb/entities/delete", body);
        } catch (Exception ex) {
            throw new BusinessException("KB_MILVUS_005", "知识版本分片清理Milvus失败：" + ex.getMessage());
        }
    }

    public void dropCollection(String targetCollection) {
        if (!enabled()) {
            return;
        }
        try {
            ensureDatabase();
            if (!hasCollection(targetCollection)) {
                return;
            }
            JSONObject body = new JSONObject();
            body.put("collectionName", safeCollectionName(targetCollection));
            fillDatabase(body);
            request("POST", "/v2/vectordb/collections/drop", body);
        } catch (Exception ex) {
            throw new BusinessException("KB_MILVUS_007", "Milvus集合删除失败：" + ex.getMessage());
        }
    }

    private boolean hasCollection(String targetCollection) throws Exception {
        JSONObject body = new JSONObject();
        body.put("collectionName", safeCollectionName(targetCollection));
        fillDatabase(body);
        String responseText = request("POST", "/v2/vectordb/collections/has", body);
        JSONObject response = JSON.parseObject(responseText);
        return collectionExists(response);
    }

    private void ensureDatabase() throws Exception {
        if (!StringUtils.hasText(database)) {
            return;
        }
        JSONObject body = new JSONObject();
        String responseText = request("POST", "/v2/vectordb/databases/list", body);
        JSONObject response = JSON.parseObject(responseText);
        JSONArray data = response.getJSONArray("data");
        if (containsText(data, database.trim())) {
            return;
        }
        JSONObject createBody = new JSONObject();
        createBody.put("dbName", database.trim());
        request("POST", "/v2/vectordb/databases/create", createBody);
    }

    private void ensureCollection(String targetCollection, int dimension) throws Exception {
        String collectionName = safeCollectionName(targetCollection);
        JSONObject hasBody = new JSONObject();
        hasBody.put("collectionName", collectionName);
        fillDatabase(hasBody);
        String responseText = request("POST", "/v2/vectordb/collections/has", hasBody);
        JSONObject response = JSON.parseObject(responseText);
        if (collectionExists(response)) {
            return;
        }
        JSONObject createBody = new JSONObject();
        createBody.put("collectionName", collectionName);
        createBody.put("dimension", dimension);
        createBody.put("metricType", "COSINE");
        createBody.put("primaryFieldName", "id");
        createBody.put("vectorFieldName", "vector");
        createBody.put("enableDynamicField", true);
        fillDatabase(createBody);
        request("POST", "/v2/vectordb/collections/create", createBody);
    }

    private boolean collectionExists(JSONObject response) {
        Object data = response.get("data");
        if (data instanceof Boolean) {
            return ((Boolean) data).booleanValue();
        }
        if (data instanceof JSONObject) {
            JSONObject dataObject = (JSONObject) data;
            return dataObject.getBooleanValue("has");
        }
        return false;
    }

    private JSONObject buildInsertBody(
            KnowledgeChunkEntity chunk, List<Float> embedding, String targetCollection) {
        JSONObject body = new JSONObject();
        body.put("collectionName", safeCollectionName(targetCollection));
        fillDatabase(body);
        JSONArray data = new JSONArray();
        JSONObject row = new JSONObject();
        row.put("id", chunk.getId());
        row.put("tenant_id", chunk.getTenantId());
        row.put("document_id", String.valueOf(chunk.getDocumentId()));
        row.put("document_version_id", String.valueOf(chunk.getDocumentVersionId()));
        row.put("index_generation_id", String.valueOf(chunk.getIndexGenerationId()));
        row.put("chunk_id", String.valueOf(chunk.getId()));
        row.put("title", chunk.getTitle());
        row.put("content", chunk.getContent());
        row.put("category", chunk.getCategory());
        row.put("source_type", chunk.getSourceType());
        row.put("source_url", chunk.getSourceUrl());
        row.put("index_version", chunk.getIndexVersion());
        row.put("index_hash", chunk.getIndexHash());
        row.put("vector", embedding);
        data.add(row);
        body.put("data", data);
        return body;
    }

    private JSONObject buildSearchBody(
            String targetCollection, Long tenantId, List<Float> embedding, int topK) {
        JSONObject body = new JSONObject();
        body.put("collectionName", safeCollectionName(targetCollection));
        body.put("annsField", "vector");
        body.put("limit", topK);
        body.put("filter", "tenant_id == " + tenantId);
        fillDatabase(body);
        JSONArray data = new JSONArray();
        data.add(embedding);
        body.put("data", data);
        JSONArray outputFields = new JSONArray();
        outputFields.add("chunk_id");
        outputFields.add("document_id");
        outputFields.add("document_version_id");
        outputFields.add("index_generation_id");
        outputFields.add("title");
        outputFields.add("content");
        outputFields.add("category");
        outputFields.add("source_type");
        outputFields.add("source_url");
        outputFields.add("index_version");
        body.put("outputFields", outputFields);
        return body;
    }

    private void fillHits(List<KnowledgeSearchHit> hits, JSONArray data) {
        for (int i = 0; i < data.size(); i++) {
            Object item = data.get(i);
            if (item instanceof JSONArray) {
                fillHits(hits, (JSONArray) item);
                continue;
            }
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject row = (JSONObject) item;
            JSONObject entity = row.getJSONObject("entity");
            if (entity == null) {
                entity = row;
            }
            KnowledgeSearchHit hit = new KnowledgeSearchHit();
            hit.setChunkId(firstText(entity.getString("chunk_id"), row.getString("id")));
            hit.setDocumentId(entity.getString("document_id"));
            hit.setTitle(entity.getString("title"));
            hit.setCategory(entity.getString("category"));
            hit.setSourceType(entity.getString("source_type"));
            hit.setSourceUrl(entity.getString("source_url"));
            hit.setContent(entity.getString("content"));
            hit.setIndexVersion(entity.getInteger("index_version"));
            hit.setScore(resolveScore(row));
            hit.setMatchType("MILVUS");
            hits.add(hit);
        }
    }

    private Double resolveScore(JSONObject row) {
        Double distance = row.getDouble("distance");
        if (distance != null) {
            return distance;
        }
        Double score = row.getDouble("score");
        return score == null ? null : score;
    }

    private String request(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUri() + path).openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json");
        if (StringUtils.hasText(token)) {
            connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
        if (body != null) {
            connection.setDoOutput(true);
            OutputStream outputStream = connection.getOutputStream();
            try {
                outputStream.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
            } finally {
                outputStream.close();
            }
        }
        int statusCode = connection.getResponseCode();
        String responseText = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException("KB_MILVUS_004", "Milvus接口调用失败：" + shrink(responseText));
        }
        validateMilvusResponse(responseText);
        return responseText;
    }

    private void validateMilvusResponse(String responseText) {
        if (!StringUtils.hasText(responseText)) {
            return;
        }
        try {
            JSONObject response = JSON.parseObject(responseText);
            Integer code = response.getInteger("code");
            if (code == null || code.intValue() == 0) {
                return;
            }
            String message = response.getString("message");
            throw new BusinessException(
                    "KB_MILVUS_004",
                    "Milvus接口调用失败：" + code + " " + shrink(message));
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return;
        }
    }

    private String readResponse(HttpURLConnection connection, int statusCode) throws Exception {
        InputStream inputStream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (inputStream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        try {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }

    private void fillDatabase(JSONObject body) {
        if (StringUtils.hasText(database)) {
            body.put("dbName", database.trim());
        }
    }

    private String baseUri() {
        String value = endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String safeCollectionName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("KB_MILVUS_006", "Milvus集合名称不能为空");
        }
        String text = value.trim();
        if (!text.matches("[a-zA-Z0-9_]+")) {
            throw new BusinessException("KB_MILVUS_006", "Milvus集合名称不合法");
        }
        return text;
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
    }

    private boolean containsText(JSONArray data, String value) {
        if (data == null || !StringUtils.hasText(value)) {
            return false;
        }
        for (int i = 0; i < data.size(); i++) {
            if (value.equals(String.valueOf(data.get(i)))) {
                return true;
            }
        }
        return false;
    }

    private String shrink(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() > 600 ? text.substring(0, 600) : text;
    }
}
