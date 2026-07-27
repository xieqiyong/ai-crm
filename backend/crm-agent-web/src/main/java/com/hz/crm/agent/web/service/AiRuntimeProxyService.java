package com.hz.crm.agent.web.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.domain.AgentMcpEntity;
import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import com.hz.crm.agent.runtime.service.AgentDefinitionService;
import com.hz.crm.agent.runtime.service.AgentRuntimePromptService;
import com.hz.crm.agent.runtime.service.AgentRuntimeSceneService;
import com.hz.crm.agent.web.dto.AiRuntimeProxyRequest;
import com.hz.crm.agent.web.dto.AiRuntimeProxyResponse;
import com.hz.crm.agent.web.dto.LeadAiAnalyzeRequest;
import com.hz.crm.application.lead.LeadApplicationService;
import com.hz.crm.application.lead.dto.LeadResponse;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.json.Jsons;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiRuntimeProxyService {

    private static final String LEAD_ANALYZE_SCENE = "LEAD_ANALYZE";

    @Autowired
    private AiRuntimeProxyProperties aiRuntimeProxyProperties;

    @Autowired
    private AgentDefinitionService agentDefinitionService;

    @Autowired
    private AgentRuntimeSceneService agentRuntimeSceneService;

    @Autowired
    private AgentRuntimePromptService agentRuntimePromptService;

    @Autowired
    private LeadApplicationService leadApplicationService;

    public AiRuntimeProxyResponse run(Long tenantId, Long userId, AiRuntimeProxyRequest request) {
        AgentRuntimeRequest runtimeRequest = buildRuntimeRequest(tenantId, userId, request);
        agentRuntimeSceneService.prepare(runtimeRequest);
        return callRuntime(runtimeRequest);
    }

    public AiRuntimeProxyResponse analyzeLead(
            Long tenantId, Long userId, String dataScope, LeadAiAnalyzeRequest request) {
        if (request == null || request.getLeadId() == null) {
            throw new BusinessException("AI_RUNTIME_LEAD_001", "线索编号不能为空");
        }
        LeadResponse lead = leadApplicationService.detail(tenantId, userId, dataScope, request.getLeadId());
        AiRuntimeProxyRequest proxyRequest = new AiRuntimeProxyRequest();
        proxyRequest.setSceneCode(LEAD_ANALYZE_SCENE);
        proxyRequest.setSessionId("lead-langgraph-analysis-" + lead.getId());
        proxyRequest.setBusinessType("LEAD");
        proxyRequest.setBusinessId(String.valueOf(lead.getId()));
        proxyRequest.setInjectedPrompt(request.getInstruction());
        proxyRequest.setMessage(buildLeadMessage(lead, request.getInstruction()));
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("businessType", "LEAD");
        context.put("leadId", String.valueOf(lead.getId()));
        context.put("lead", lead);
        proxyRequest.setContext(context);
        return run(tenantId, userId, proxyRequest);
    }

    private AgentRuntimeRequest buildRuntimeRequest(Long tenantId, Long userId, AiRuntimeProxyRequest request) {
        if (request == null) {
            throw new BusinessException("AI_RUNTIME_001", "AI运行请求不能为空");
        }
        if (blank(request.getMessage())) {
            throw new BusinessException("AI_RUNTIME_002", "消息内容不能为空");
        }
        AgentRuntimeRequest runtimeRequest = new AgentRuntimeRequest();
        runtimeRequest.setTenantId(tenantId);
        runtimeRequest.setUserId(userId);
        runtimeRequest.setRunId(request.getRunId());
        runtimeRequest.setConversationId(request.getConversationId());
        runtimeRequest.setMessage(request.getMessage());
        runtimeRequest.setSessionId(trimToNull(request.getSessionId()));
        runtimeRequest.setInjectedPrompt(trimToNull(request.getInjectedPrompt()));
        runtimeRequest.setSceneCode(trimToNull(request.getSceneCode()));
        runtimeRequest.setBusinessType(trimToNull(request.getBusinessType()));
        runtimeRequest.setBusinessId(trimToNull(request.getBusinessId()));
        runtimeRequest.setContext(request.getContext() == null ? new HashMap<String, Object>() : request.getContext());
        if (request.getAgentId() != null) {
            AgentEntity agent = agentDefinitionService.detail(tenantId, request.getAgentId());
            runtimeRequest.setAgent(agent);
        }
        return runtimeRequest;
    }

    private AiRuntimeProxyResponse callRuntime(AgentRuntimeRequest runtimeRequest) {
        String requestText = buildRuntimePayload(runtimeRequest).toJSONString();
        try {
            URL url = new URL(resolveRuntimeUrl("/internal/ai/runtime/run"));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(resolveTimeoutMs());
            connection.setReadTimeout(resolveTimeoutMs());
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("X-Tenant-Id", String.valueOf(runtimeRequest.getTenantId()));
            connection.setRequestProperty("X-User-Id", String.valueOf(runtimeRequest.getUserId()));
            if (!blank(aiRuntimeProxyProperties.getInternalToken())) {
                connection.setRequestProperty("X-Internal-Token", aiRuntimeProxyProperties.getInternalToken());
            }
            if (!blank(MDC.get("traceId"))) {
                connection.setRequestProperty("X-Trace-Id", MDC.get("traceId"));
            }
            writeRequest(connection, requestText);
            int statusCode = connection.getResponseCode();
            String responseText = readResponse(connection, statusCode);
            if (statusCode < 200 || statusCode >= 300) {
                throw new BusinessException("AI_RUNTIME_004", "AI Runtime响应异常：" + shrink(responseText, 500));
            }
            AiRuntimeProxyResponse response = Jsons.parseObject(responseText, AiRuntimeProxyResponse.class);
            if (response == null) {
                throw new BusinessException("AI_RUNTIME_005", "AI Runtime响应为空");
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("AI_RUNTIME_006", "AI Runtime调用失败：" + ex.getMessage());
        }
    }

    private JSONObject buildRuntimePayload(AgentRuntimeRequest request) {
        JSONObject payload = new JSONObject();
        payload.put("tenantId", String.valueOf(request.getTenantId()));
        payload.put("userId", String.valueOf(request.getUserId()));
        payload.put("runId", idToString(request.getRunId()));
        payload.put("conversationId", idToString(request.getConversationId()));
        payload.put("sceneCode", request.getSceneCode());
        payload.put("businessType", request.getBusinessType());
        payload.put("businessId", request.getBusinessId());
        payload.put("message", request.getMessage());
        payload.put("sessionId", request.getSessionId());
        payload.put("injectedPrompt", request.getInjectedPrompt());
        payload.put("context", request.getContext());
        payload.put("agent", buildAgentPayload(request.getAgent()));
        payload.put("mcps", buildMcpPayload(request.getMcps()));
        payload.put("skills", buildSkillPayload(request.getSkills()));
        payload.put("renderedSystemPrompt", agentRuntimePromptService.render(
                request.getAgent().getSystemPrompt(), request.getInjectedPrompt(), request.getContext()));
        return payload;
    }

    private JSONObject buildAgentPayload(AgentEntity agent) {
        JSONObject value = new JSONObject();
        value.put("id", idToString(agent.getId()));
        value.put("code", agent.getCode());
        value.put("sceneCode", agent.getSceneCode());
        value.put("sceneName", agent.getSceneName());
        value.put("name", agent.getName());
        value.put("description", agent.getDescription());
        value.put("systemPrompt", agent.getSystemPrompt());
        value.put("modelProvider", agent.getModelProvider());
        value.put("modelName", agent.getModelName());
        value.put("baseUrl", agent.getBaseUrl());
        value.put("apiKey", agent.getApiKey());
        value.put("maxIters", agent.getMaxIters());
        value.put("extraConfigJson", agent.getExtraConfigJson());
        return value;
    }

    private JSONArray buildMcpPayload(List<AgentMcpEntity> mcps) {
        JSONArray array = new JSONArray();
        if (mcps == null) {
            return array;
        }
        for (AgentMcpEntity item : mcps) {
            JSONObject value = new JSONObject();
            value.put("id", idToString(item.getId()));
            value.put("name", item.getName());
            value.put("transportType", item.getTransportType());
            value.put("endpoint", item.getEndpoint());
            value.put("command", item.getCommand());
            value.put("argumentsJson", item.getArgumentsJson());
            value.put("headersJson", item.getHeadersJson());
            array.add(value);
        }
        return array;
    }

    private JSONArray buildSkillPayload(List<AgentSkillEntity> skills) {
        JSONArray array = new JSONArray();
        if (skills == null) {
            return array;
        }
        for (AgentSkillEntity item : skills) {
            JSONObject value = new JSONObject();
            value.put("id", idToString(item.getId()));
            value.put("code", item.getSkillKey());
            value.put("name", item.getName());
            value.put("content", item.getContent());
            array.add(value);
        }
        return array;
    }

    private String buildLeadMessage(LeadResponse lead, String instruction) {
        StringBuilder builder = new StringBuilder();
        builder.append("请使用 LangGraph 线索分析场景分析以下真实线索数据。");
        builder.append("最终输出合法 JSON 对象，不要输出 Markdown 或额外解释。");
        if (!blank(instruction)) {
            builder.append("补充要求：").append(instruction.trim());
        }
        builder.append("线索真实数据：").append(Jsons.toJson(lead));
        return builder.toString();
    }

    private void writeRequest(HttpURLConnection connection, String requestText) throws Exception {
        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
        try {
            writer.write(requestText);
            writer.flush();
        } finally {
            writer.close();
        }
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

    private String resolveRuntimeUrl(String path) {
        if (blank(aiRuntimeProxyProperties.getUrl())) {
            throw new BusinessException("AI_RUNTIME_003", "AI Runtime地址未配置");
        }
        return trimRightSlash(aiRuntimeProxyProperties.getUrl().trim()) + path;
    }

    private int resolveTimeoutMs() {
        Integer timeoutMs = aiRuntimeProxyProperties.getTimeoutMs();
        if (timeoutMs == null || timeoutMs.intValue() <= 0) {
            return 90000;
        }
        return timeoutMs.intValue();
    }

    private String idToString(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimRightSlash(String value) {
        String text = value;
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String trimToNull(String value) {
        if (blank(value)) {
            return null;
        }
        return value.trim();
    }

    private String shrink(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
