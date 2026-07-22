package com.hz.crm.agent.runtime.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.agent.runtime.domain.ModelConfigEntity;
import com.hz.crm.agent.runtime.dto.ModelConfigDebugRequest;
import com.hz.crm.agent.runtime.dto.ModelConfigDebugResponse;
import com.hz.crm.agent.runtime.dto.ModelConfigIdRequest;
import com.hz.crm.agent.runtime.dto.ModelConfigRequest;
import com.hz.crm.agent.runtime.dto.ModelConfigResponse;
import com.hz.crm.agent.runtime.dto.ModelConfigStatusResponse;
import com.hz.crm.agent.runtime.repository.ModelConfigRepository;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelConfigService {

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(readOnly = true)
    public List<ModelConfigResponse> list(String tenantId) {
        List<ModelConfigEntity> entities = modelConfigRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId);
        List<ModelConfigResponse> responses = new ArrayList<ModelConfigResponse>();
        for (ModelConfigEntity entity : entities) {
            responses.add(toResponse(entity));
        }
        return responses;
    }

    @Transactional
    public ModelConfigResponse save(String tenantId, ModelConfigRequest request) {
        if (request == null || blank(request.getName()) || blank(request.getModelName()) || blank(request.getApiKeyEnv())) {
            throw new BusinessException("MODEL_001", "模型名称、模型标识和密钥环境变量不能为空");
        }
        ModelConfigEntity entity;
        if (request.getId() == null) {
            entity = new ModelConfigEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findConfig(tenantId, request.getId());
        }
        entity.setProvider(blank(request.getProvider()) ? "OPENAI" : request.getProvider().trim());
        entity.setName(request.getName().trim());
        entity.setModelName(request.getModelName().trim());
        entity.setBaseUrl(trimToNull(request.getBaseUrl()));
        entity.setApiKeyEnv(request.getApiKeyEnv().trim());
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setDefaultConfig(request.getDefaultConfig() != null && request.getDefaultConfig());
        if (entity.isDefaultConfig()) {
            clearDefault(tenantId, entity.getId());
        }
        return toResponse(modelConfigRepository.save(entity));
    }

    @Transactional
    public void delete(String tenantId, ModelConfigIdRequest request) {
        ModelConfigEntity entity = findConfig(tenantId, request == null ? null : request.getId());
        entity.setDeleted(true);
        entity.setDefaultConfig(false);
        modelConfigRepository.save(entity);
    }

    @Transactional
    public ModelConfigResponse setDefault(String tenantId, ModelConfigIdRequest request) {
        ModelConfigEntity entity = findConfig(tenantId, request == null ? null : request.getId());
        clearDefault(tenantId, entity.getId());
        entity.setDefaultConfig(true);
        entity.setEnabled(true);
        return toResponse(modelConfigRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ModelConfigStatusResponse status(String tenantId, ModelConfigIdRequest request) {
        ModelConfigEntity entity = findConfig(tenantId, request == null ? null : request.getId());
        ModelConfigStatusResponse response = new ModelConfigStatusResponse();
        response.setId(idToString(entity.getId()));
        boolean available = System.getenv(entity.getApiKeyEnv()) != null
                && System.getenv(entity.getApiKeyEnv()).trim().length() > 0;
        response.setAvailable(available);
        response.setMessage(available ? "密钥环境变量已配置" : "密钥环境变量未配置");
        return response;
    }

    @Transactional(readOnly = true)
    public ModelConfigDebugResponse debug(String tenantId, ModelConfigDebugRequest request) {
        ModelConfigEntity entity = findConfig(tenantId, request == null ? null : request.getId());
        ModelConfigDebugResponse response = new ModelConfigDebugResponse();
        response.setId(idToString(entity.getId()));
        if (!entity.isEnabled()) {
            response.setSuccess(false);
            response.setMessage("模型配置已停用");
            return response;
        }
        String apiKey = entity.getApiKeyEnv();
        if (blank(apiKey)) {
            response.setSuccess(false);
            response.setMessage("密钥环境变量未配置");
            return response;
        }
        long startedAt = System.currentTimeMillis();
        try {
            String output = requestChatCompletion(entity, apiKey, request);
            response.setSuccess(true);
            response.setMessage("模型调试成功");
            response.setOutput(output);
        } catch (Exception ex) {
            response.setSuccess(false);
            response.setMessage(ex.getMessage());
        }
        response.setElapsedMs(System.currentTimeMillis() - startedAt);
        return response;
    }

    private void clearDefault(String tenantId, Long keepId) {
        List<ModelConfigEntity> defaults =
                modelConfigRepository.findByTenantIdAndDefaultConfigTrueAndDeletedFalse(tenantId);
        for (ModelConfigEntity item : defaults) {
            if (!item.getId().equals(keepId)) {
                item.setDefaultConfig(false);
                modelConfigRepository.save(item);
            }
        }
    }

    private ModelConfigEntity findConfig(String tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("MODEL_002", "模型配置编号不能为空");
        }
        return modelConfigRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("MODEL_003", "模型配置不存在"));
    }

    private String requestChatCompletion(
            ModelConfigEntity entity, String apiKey, ModelConfigDebugRequest request) throws Exception {
        String prompt = request == null ? null : request.getPrompt();
        if (blank(prompt)) {
            prompt = "请回复：模型连接成功";
        }
        String requestBody = buildChatBody(entity.getModelName(), prompt);
        byte[] requestBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        URL url = new URL(resolveChatUrl(entity));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int timeout = resolveTimeout(request);
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        OutputStream outputStream = connection.getOutputStream();
        try {
            outputStream.write(requestBytes);
        } finally {
            outputStream.close();
        }
        int statusCode = connection.getResponseCode();
        String responseText = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException("MODEL_004", "模型接口调用失败：" + shrink(responseText));
        }
        return extractModelOutput(responseText);
    }

    private String buildChatBody(String modelName, String prompt) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", modelName);
        body.put("temperature", 0);
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        Map<String, String> systemMessage = new LinkedHashMap<String, String>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是智能营销管理系统的大模型连通性检测助手，请用中文简短回复。");
        messages.add(systemMessage);
        Map<String, String> userMessage = new LinkedHashMap<String, String>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        body.put("messages", messages);
        return JSON.toJSONString(body);
    }

    private String resolveChatUrl(ModelConfigEntity entity) {
        String baseUrl = trimToNull(entity.getBaseUrl());
        if (baseUrl == null) {
            baseUrl = defaultBaseUrl(entity.getProvider());
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        return baseUrl + "/chat/completions";
    }

    private String defaultBaseUrl(String provider) {
        if ("DEEPSEEK".equalsIgnoreCase(provider)) {
            return "https://api.deepseek.com/v1";
        }
        if ("DASHSCOPE".equalsIgnoreCase(provider)) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return "https://api.openai.com/v1";
    }

    private int resolveTimeout(ModelConfigDebugRequest request) {
        int seconds = request == null || request.getTimeoutSeconds() == null ? 20 : request.getTimeoutSeconds();
        if (seconds < 3) {
            seconds = 3;
        }
        if (seconds > 60) {
            seconds = 60;
        }
        return seconds * 1000;
    }

    private String readResponse(HttpURLConnection connection, int statusCode) throws Exception {
        InputStream inputStream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } finally {
            reader.close();
        }
        return builder.toString();
    }

    private String extractModelOutput(String responseText) {
        JSONObject jsonObject = JSON.parseObject(responseText);
        JSONArray choices = jsonObject.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return shrink(responseText);
        }
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice.getJSONObject("message");
        if (message == null) {
            return shrink(responseText);
        }
        String content = message.getString("content");
        if (blank(content)) {
            return shrink(responseText);
        }
        return shrink(content);
    }

    private String shrink(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() > 600) {
            return text.substring(0, 600);
        }
        return text;
    }

    private ModelConfigResponse toResponse(ModelConfigEntity entity) {
        ModelConfigResponse response = new ModelConfigResponse();
        response.setId(idToString(entity.getId()));
        response.setProvider(entity.getProvider());
        response.setName(entity.getName());
        response.setModelName(entity.getModelName());
        response.setBaseUrl(entity.getBaseUrl());
        response.setApiKeyEnv(entity.getApiKeyEnv());
        response.setRemark(entity.getRemark());
        response.setDefaultConfig(entity.isDefaultConfig());
        response.setEnabled(entity.isEnabled());
        response.setApiKeyConfigured(System.getenv(entity.getApiKeyEnv()) != null
                && System.getenv(entity.getApiKeyEnv()).trim().length() > 0);
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private String trimToNull(String value) {
        if (blank(value)) {
            return null;
        }
        return value.trim();
    }

    private String idToString(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
