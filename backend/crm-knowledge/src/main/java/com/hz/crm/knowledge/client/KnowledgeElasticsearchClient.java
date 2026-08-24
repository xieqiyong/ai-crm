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
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeElasticsearchClient {

    @Value("${crm.search.elasticsearch.enabled:false}")
    private boolean enabled;

    @Value("${crm.search.elasticsearch.uris:http://localhost:9200}")
    private String uris;

    @Value("${crm.search.elasticsearch.username:}")
    private String username;

    @Value("${crm.search.elasticsearch.password:}")
    private String password;

    @Value("${crm.knowledge.elasticsearch.index-name:crm_knowledge_chunk}")
    private String indexName;

    @Value("${crm.knowledge.elasticsearch.timeout-ms:10000}")
    private int timeoutMs;

    public boolean enabled() {
        return enabled && StringUtils.hasText(uris);
    }

    public String generationIndexName(Long generationId) {
        return safeIndexName(indexName) + "_g_" + generationId;
    }

    public void index(KnowledgeChunkEntity chunk) {
        index(chunk, indexName);
    }

    public void index(KnowledgeChunkEntity chunk, String targetIndex) {
        if (!enabled()) {
            return;
        }
        try {
            request("PUT", baseUri() + "/" + safeIndexName(targetIndex) + "/_doc/" + chunk.getId(), buildDocument(chunk));
        } catch (Exception ex) {
            throw new BusinessException("KB_ES_001", "知识分片写入ES失败：" + ex.getMessage());
        }
    }

    public void deleteByDocumentId(Long tenantId, Long documentId) {
        deleteByDocumentId(tenantId, documentId, indexName);
    }

    public void deleteByDocumentId(Long tenantId, Long documentId, String targetIndex) {
        if (!enabled() || tenantId == null || documentId == null) {
            return;
        }
        try {
            JSONObject body = new JSONObject();
            JSONObject bool = new JSONObject();
            JSONArray filter = new JSONArray();
            filter.add(term("tenantId", tenantId));
            filter.add(term("documentId", String.valueOf(documentId)));
            bool.put("filter", filter);
            JSONObject query = new JSONObject();
            query.put("bool", bool);
            body.put("query", query);
            requestAllowNotFound(
                    "POST", baseUri() + "/" + safeIndexName(targetIndex) + "/_delete_by_query", body);
        } catch (Exception ex) {
            throw new BusinessException("KB_ES_004", "知识分片清理ES失败：" + ex.getMessage());
        }
    }

    public void deleteByDocumentVersion(
            Long tenantId, Long documentId, Long documentVersionId, String targetIndex) {
        if (!enabled() || tenantId == null || documentId == null || documentVersionId == null) {
            return;
        }
        try {
            JSONObject body = new JSONObject();
            JSONObject bool = new JSONObject();
            JSONArray filter = new JSONArray();
            filter.add(term("tenantId", tenantId));
            filter.add(term("documentId", String.valueOf(documentId)));
            filter.add(term("documentVersionId", String.valueOf(documentVersionId)));
            bool.put("filter", filter);
            JSONObject query = new JSONObject();
            query.put("bool", bool);
            body.put("query", query);
            requestAllowNotFound(
                    "POST", baseUri() + "/" + safeIndexName(targetIndex) + "/_delete_by_query", body);
        } catch (Exception ex) {
            throw new BusinessException("KB_ES_004", "知识版本分片清理ES失败：" + ex.getMessage());
        }
    }

    public void deleteIndex(String targetIndex) {
        if (!enabled()) {
            return;
        }
        try {
            requestAllowNotFound("DELETE", baseUri() + "/" + safeIndexName(targetIndex), null);
        } catch (Exception ex) {
            throw new BusinessException("KB_ES_006", "ES索引删除失败：" + ex.getMessage());
        }
    }

    public List<KnowledgeSearchHit> search(Long tenantId, String query, String category, String sourceType, int topK) {
        return search(indexName, tenantId, query, category, sourceType, topK);
    }

    public List<KnowledgeSearchHit> search(
            String targetIndex,
            Long tenantId,
            String query,
            String category,
            String sourceType,
            int topK) {
        List<KnowledgeSearchHit> hits = new ArrayList<KnowledgeSearchHit>();
        if (!enabled() || !StringUtils.hasText(query)) {
            return hits;
        }
        try {
            JSONObject body = buildSearchBody(tenantId, query, category, sourceType, topK);
            String responseText =
                    request("POST", baseUri() + "/" + safeIndexName(targetIndex) + "/_search", body);
            JSONObject response = JSON.parseObject(responseText);
            JSONObject hitsObject = response.getJSONObject("hits");
            JSONArray sourceHits = hitsObject == null ? null : hitsObject.getJSONArray("hits");
            if (sourceHits == null) {
                return hits;
            }
            for (int i = 0; i < sourceHits.size(); i++) {
                JSONObject hitObject = sourceHits.getJSONObject(i);
                JSONObject source = hitObject.getJSONObject("_source");
                if (source == null) {
                    continue;
                }
                hits.add(toHit(hitObject, source));
            }
            return hits;
        } catch (Exception ex) {
            throw new BusinessException("KB_ES_002", "知识库ES检索失败：" + ex.getMessage());
        }
    }

    private JSONObject buildDocument(KnowledgeChunkEntity chunk) {
        JSONObject document = new JSONObject();
        document.put("tenantId", chunk.getTenantId());
        document.put("documentId", String.valueOf(chunk.getDocumentId()));
        document.put("documentVersionId", String.valueOf(chunk.getDocumentVersionId()));
        document.put("indexGenerationId", String.valueOf(chunk.getIndexGenerationId()));
        document.put("chunkId", String.valueOf(chunk.getId()));
        document.put("title", chunk.getTitle());
        document.put("sourceType", chunk.getSourceType());
        document.put("category", chunk.getCategory());
        document.put("tags", chunk.getTags());
        document.put("sourceUrl", chunk.getSourceUrl());
        document.put("content", chunk.getContent());
        document.put("indexVersion", chunk.getIndexVersion());
        document.put("indexHash", chunk.getIndexHash());
        document.put("updatedAt", chunk.getUpdatedAt() == null ? "" : chunk.getUpdatedAt().toString());
        return document;
    }

    private JSONObject buildSearchBody(
            Long tenantId, String query, String category, String sourceType, int topK) {
        JSONObject body = new JSONObject();
        body.put("size", topK);
        JSONObject bool = new JSONObject();
        JSONArray must = new JSONArray();
        JSONObject multiMatch = new JSONObject();
        multiMatch.put("query", query);
        JSONArray fields = new JSONArray();
        fields.add("title^2");
        fields.add("content");
        fields.add("tags");
        fields.add("category");
        multiMatch.put("fields", fields);
        JSONObject mustQuery = new JSONObject();
        mustQuery.put("multi_match", multiMatch);
        must.add(mustQuery);
        bool.put("must", must);
        JSONArray filter = new JSONArray();
        filter.add(term("tenantId", tenantId));
        if (StringUtils.hasText(category)) {
            filter.add(term("category", category.trim()));
        }
        if (StringUtils.hasText(sourceType)) {
            filter.add(term("sourceType", sourceType.trim()));
        }
        bool.put("filter", filter);
        JSONObject queryObject = new JSONObject();
        queryObject.put("bool", bool);
        body.put("query", queryObject);
        return body;
    }

    private JSONObject term(String field, Object value) {
        JSONObject termValue = new JSONObject();
        termValue.put(field, value);
        JSONObject term = new JSONObject();
        term.put("term", termValue);
        return term;
    }

    private KnowledgeSearchHit toHit(JSONObject hitObject, JSONObject source) {
        KnowledgeSearchHit hit = new KnowledgeSearchHit();
        hit.setChunkId(source.getString("chunkId"));
        hit.setDocumentId(source.getString("documentId"));
        hit.setTitle(source.getString("title"));
        hit.setCategory(source.getString("category"));
        hit.setSourceType(source.getString("sourceType"));
        hit.setSourceUrl(source.getString("sourceUrl"));
        hit.setContent(source.getString("content"));
        hit.setIndexVersion(source.getInteger("indexVersion"));
        hit.setScore(hitObject.getDouble("_score"));
        hit.setMatchType("ES");
        return hit;
    }

    private String request(String method, String url, JSONObject body) throws Exception {
        return request(method, url, body, false);
    }

    private String requestAllowNotFound(String method, String url, JSONObject body) throws Exception {
        return request(method, url, body, true);
    }

    private String request(String method, String url, JSONObject body, boolean allowNotFound) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json");
        fillAuth(connection);
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
        if (allowNotFound && statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
            return responseText;
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException("KB_ES_003", "ES接口调用失败：" + shrink(responseText));
        }
        return responseText;
    }

    private void fillAuth(HttpURLConnection connection) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        String text = username.trim() + ":" + (password == null ? "" : password);
        String auth = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + auth);
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

    private String baseUri() {
        String value = uris.split(",")[0].trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String safeIndexName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("KB_ES_005", "ES索引名称不能为空");
        }
        String text = value.trim().toLowerCase();
        if (!text.matches("[a-z0-9._-]+")) {
            throw new BusinessException("KB_ES_005", "ES索引名称不合法");
        }
        return text;
    }

    private String shrink(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() > 600 ? text.substring(0, 600) : text;
    }
}
