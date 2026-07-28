package com.hz.crm.knowledge.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.common.exception.BusinessException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeEmbeddingClient {

    @Value("${crm.knowledge.embedding.enabled:false}")
    private boolean enabled;

    @Value("${crm.knowledge.embedding.base-url:}")
    private String baseUrl;

    @Value("${crm.knowledge.embedding.api-key:}")
    private String apiKey;

    @Value("${crm.knowledge.embedding.model:bge-m3:latest}")
    private String model;

    @Value("${crm.knowledge.embedding.dimensions:0}")
    private int dimensions;

    @Value("${crm.knowledge.embedding.timeout-ms:30000}")
    private int timeoutMs;

    public boolean enabled() {
        return enabled && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(model);
    }

    public String model() {
        return model;
    }

    public int dimensions() {
        return dimensions;
    }

    public List<Float> embed(String text) {
        if (!enabled()) {
            throw new BusinessException("KB_EMBED_001", "知识库向量模型未配置");
        }
        try {
            String requestBody = buildRequestBody(text);
            HttpURLConnection connection = openConnection(resolveEmbeddingUrl());
            writeRequest(connection, requestBody);
            int statusCode = connection.getResponseCode();
            String responseText = readResponse(connection, statusCode);
            if (statusCode < 200 || statusCode >= 300) {
                throw new BusinessException("KB_EMBED_002", "向量模型调用失败：" + shrink(responseText));
            }
            return parseEmbedding(responseText);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("KB_EMBED_003", "向量模型调用异常：" + ex.getMessage());
        }
    }

    private String buildRequestBody(String text) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("input", text == null ? "" : text);
        if (dimensions > 0) {
            body.put("dimensions", dimensions);
        }
        return JSON.toJSONString(body);
    }

    private String resolveEmbeddingUrl() {
        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/embeddings")) {
            return value;
        }
        return value + "/embeddings";
    }

    private HttpURLConnection openConnection(String urlValue) throws Exception {
        URL url = new URL(urlValue);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        if (StringUtils.hasText(apiKey)) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        }
        return connection;
    }

    private void writeRequest(HttpURLConnection connection, String requestBody) throws Exception {
        byte[] requestBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        OutputStream outputStream = connection.getOutputStream();
        try {
            outputStream.write(requestBytes);
        } finally {
            outputStream.close();
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

    private List<Float> parseEmbedding(String responseText) {
        JSONObject jsonObject = JSON.parseObject(responseText);
        JSONArray data = jsonObject.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new BusinessException("KB_EMBED_004", "向量模型未返回向量");
        }
        JSONObject first = data.getJSONObject(0);
        JSONArray embedding = first.getJSONArray("embedding");
        if (embedding == null || embedding.isEmpty()) {
            throw new BusinessException("KB_EMBED_004", "向量模型未返回向量");
        }
        List<Float> values = new ArrayList<Float>();
        for (int i = 0; i < embedding.size(); i++) {
            values.add(Float.valueOf(embedding.getFloatValue(i)));
        }
        return values;
    }

    private String shrink(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() > 600 ? text.substring(0, 600) : text;
    }
}
