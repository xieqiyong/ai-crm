package com.hz.crm.agent.web.service;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.agent.runtime.domain.AgentEntity;
import com.hz.crm.agent.runtime.domain.AgentRunEntity;
import com.hz.crm.agent.runtime.domain.ConversationEntity;
import com.hz.crm.agent.runtime.dto.AgentRunRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.agent.runtime.mapper.AgentMapper;
import com.hz.crm.agent.runtime.mapper.AgentRunMapper;
import com.hz.crm.agent.runtime.mapper.ConversationMapper;
import com.hz.crm.agent.runtime.service.AgentRunService;
import com.hz.crm.agent.web.dto.AgentAssistantAgentResponse;
import com.hz.crm.agent.web.dto.AgentAssistantAttachmentRequest;
import com.hz.crm.agent.web.dto.AgentAssistantConversationActionRequest;
import com.hz.crm.agent.web.dto.AgentAssistantConversationListRequest;
import com.hz.crm.agent.web.dto.AgentAssistantConversationResponse;
import com.hz.crm.agent.web.dto.AgentAssistantMessageResponse;
import com.hz.crm.agent.web.dto.AgentAssistantMessagesRequest;
import com.hz.crm.agent.web.dto.AgentAssistantRunRequest;
import com.hz.crm.agent.web.dto.AgentAssistantRunStopRequest;
import com.hz.crm.auth.security.CurrentUserContext;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.json.Jsons;
import com.hz.crm.common.time.DateTimes;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentAssistantWorkbenchService {

    private final ConcurrentMap<String, ActiveRun> activeRuns = new ConcurrentHashMap<String, ActiveRun>();

    @Autowired
    private AgentMapper agentMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private AgentRunMapper agentRunMapper;

    @Autowired
    private AgentRunService agentRunService;

    public List<AgentAssistantAgentResponse> agents(Long tenantId, Long userId) {
        List<AgentEntity> agents = agentMapper.selectList(Wrappers.<AgentEntity>lambdaQuery()
                .eq(AgentEntity::getTenantId, tenantId)
                .eq(AgentEntity::isDeleted, false)
                .eq(AgentEntity::isEnabled, true)
                .orderByDesc(AgentEntity::getUpdatedAt));
        List<AgentAssistantAgentResponse> responses = new ArrayList<AgentAssistantAgentResponse>();
        for (AgentEntity agent : agents) {
            responses.add(toAgentResponse(tenantId, userId, agent));
        }
        return responses;
    }

    public List<AgentAssistantConversationResponse> conversations(
            Long tenantId,
            Long userId,
            AgentAssistantConversationListRequest request) {
        LambdaQueryWrapper<ConversationEntity> wrapper = Wrappers.<ConversationEntity>lambdaQuery()
                .eq(ConversationEntity::getTenantId, tenantId)
                .eq(ConversationEntity::getUserId, userId)
                .eq(ConversationEntity::isDeleted, false);
        if (request != null && request.getAgentId() != null) {
            wrapper.eq(ConversationEntity::getAgentId, request.getAgentId());
        }
        wrapper.orderByDesc(ConversationEntity::getLastMessageAt).last("limit 50");
        List<ConversationEntity> conversations = conversationMapper.selectList(wrapper);
        List<AgentAssistantConversationResponse> responses = new ArrayList<AgentAssistantConversationResponse>();
        for (ConversationEntity conversation : conversations) {
            responses.add(toConversationResponse(conversation));
        }
        return responses;
    }

    public List<AgentAssistantMessageResponse> messages(
            Long tenantId,
            Long userId,
            AgentAssistantMessagesRequest request) {
        requireConversation(tenantId, userId, request.getConversationId(), null);
        List<AgentRunEntity> runs = agentRunMapper.selectList(Wrappers.<AgentRunEntity>lambdaQuery()
                .eq(AgentRunEntity::getTenantId, tenantId)
                .eq(AgentRunEntity::getUserId, userId)
                .eq(AgentRunEntity::getConversationId, request.getConversationId())
                .eq(AgentRunEntity::isDeleted, false)
                .orderByAsc(AgentRunEntity::getCreatedAt));
        List<AgentAssistantMessageResponse> responses = new ArrayList<AgentAssistantMessageResponse>();
        for (AgentRunEntity run : runs) {
            responses.add(userMessage(run));
            if (StringUtils.hasText(run.getOutputText())
                    || "FAILED".equals(run.getStatus())
                    || "STOPPED".equals(run.getStatus())) {
                responses.add(assistantMessage(run));
            }
        }
        return responses;
    }

    public void deleteConversation(
            Long tenantId,
            Long userId,
            AgentAssistantConversationActionRequest request) {
        ConversationEntity conversation = requireConversation(tenantId, userId, request.getConversationId(), null);
        conversation.setDeleted(true);
        conversation.setUpdatedAt(DateTimes.now());
        conversationMapper.updateById(conversation);
    }

    public SseEmitter runStream(
            Long tenantId,
            Long userId,
            AgentAssistantRunRequest request) {
        SseEmitter emitter = new SseEmitter(Long.valueOf(120000L));
        String activeRunKey = activeRunKey(tenantId, userId, request.getRequestId());
        ActiveRun activeRun = new ActiveRun();
        ActiveRun previous = activeRuns.putIfAbsent(activeRunKey, activeRun);
        if (previous != null) {
            throw new BusinessException("AGENT_ASSISTANT_005", "本次回答已经在运行中");
        }
        emitter.onTimeout(activeRun::stop);
        emitter.onError(error -> activeRun.stop());
        emitter.onCompletion(() -> activeRuns.remove(activeRunKey, activeRun));
        SecurityContext securityContext = SecurityContextHolder.getContext();
        JwtPrincipal principal = CurrentUserContext.getPrincipal();
        String token = CurrentUserContext.getToken();
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                SecurityContextHolder.setContext(securityContext);
                if (principal != null) {
                    CurrentUserContext.setPrincipal(principal);
                }
                if (StringUtils.hasText(token)) {
                    CurrentUserContext.setToken(token);
                }
                try {
                    doRunStream(tenantId, userId, request, emitter, activeRunKey, activeRun);
                } finally {
                    CurrentUserContext.clear();
                    SecurityContextHolder.clearContext();
                }
            }
        });
        return emitter;
    }

    public boolean stopRun(
            Long tenantId,
            Long userId,
            AgentAssistantRunStopRequest request) {
        ActiveRun activeRun = activeRuns.get(activeRunKey(tenantId, userId, request.getRequestId()));
        if (activeRun == null) {
            return false;
        }
        return activeRun.stop();
    }

    private void doRunStream(
            Long tenantId,
            Long userId,
            AgentAssistantRunRequest request,
            SseEmitter emitter,
            String activeRunKey,
            ActiveRun activeRun) {
        StringBuilder answer = new StringBuilder();
        Long[] runId = new Long[1];
        Long[] conversationId = new Long[] { request.getConversationId() };
        boolean[] emittedAnswer = new boolean[] { false };
        try {
            AgentRunRequest runRequest = buildRunRequest(tenantId, userId, request);
            sendRuntimeEvent(emitter, "RUN_STATUS_CHANGED", "运行状态变更", "智能体开始处理", null, null);
            agentRunService.run(tenantId, userId, runRequest)
                    .doOnSubscribe(activeRun::bind)
                    .doFinally(signalType -> activeRuns.remove(activeRunKey, activeRun))
                    .subscribe(event -> handleRuntimeEvent(
                            emitter,
                            event,
                            answer,
                            runId,
                            conversationId,
                            emittedAnswer),
                            error -> completeRunFailed(
                                    emitter, activeRun, error, runId[0], conversationId[0]),
                            () -> completeRunSuccess(
                                    emitter, activeRun, answer.toString(), runId[0], conversationId[0]));
        } catch (RuntimeException ex) {
            activeRuns.remove(activeRunKey, activeRun);
            completeRunFailed(emitter, activeRun, ex, runId[0], conversationId[0]);
        }
    }

    private void completeRunSuccess(
            SseEmitter emitter,
            ActiveRun activeRun,
            String answer,
            Long runId,
            Long conversationId) {
        if (!activeRun.finish()) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("runId", runId);
        response.put("conversationId", conversationId);
        response.put("reply", answer);
        response.put("message", "智能体回复完成");
        response.put("success", true);
        metadata.put("response", response);
        sendRuntimeEvent(emitter, "RUN_FINISHED", "运行完成", "智能体回复完成", metadata, runtimeIds(runId, conversationId));
        emitter.complete();
    }

    private void completeRunFailed(
            SseEmitter emitter,
            ActiveRun activeRun,
            Throwable error,
            Long runId,
            Long conversationId) {
        if (!activeRun.finish()) {
            return;
        }
        String message = error == null ? "智能体运行失败" : error.getMessage();
        sendRuntimeEvent(emitter, "RUN_ERROR", "运行异常", message, null, runtimeIds(runId, conversationId));
        emitter.complete();
    }

    private AgentRunRequest buildRunRequest(Long tenantId, Long userId, AgentAssistantRunRequest request) {
        AgentEntity agent = requireEnabledAgent(tenantId, request.getAgentId());
        if (request.getConversationId() != null) {
            requireConversation(tenantId, userId, request.getConversationId(), agent.getId());
        }
        AgentRunRequest runRequest = new AgentRunRequest();
        runRequest.setAgentId(agent.getId());
        runRequest.setConversationId(request.getConversationId());
        runRequest.setSessionId(resolveSessionId(userId, agent.getId(), request));
        runRequest.setSceneCode(agent.getSceneCode());
        runRequest.setMessage(buildModelMessage(tenantId, userId, request));
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("userMessage", request.getMessage());
        context.put("attachments", safeAttachments(request.getAttachments()));
        context.put("conversationTitle", title(request.getMessage()));
        runRequest.setContext(context);
        return runRequest;
    }

    private String buildModelMessage(Long tenantId, Long userId, AgentAssistantRunRequest request) {
        StringBuilder builder = new StringBuilder();
        List<AgentAssistantMessageResponse> history = recentMessages(tenantId, userId, request.getConversationId());
        if (!history.isEmpty()) {
            builder.append("最近会话：\n");
            for (AgentAssistantMessageResponse message : history) {
                builder.append("user".equals(message.getRole()) ? "用户：" : "助手：");
                builder.append(text(message.getContent())).append("\n");
            }
            builder.append("\n");
        }
        builder.append("用户问题：").append(text(request.getMessage()));
        List<AgentAssistantAttachmentRequest> attachments = safeAttachments(request.getAttachments());
        if (!attachments.isEmpty()) {
            builder.append("\n\n用户上传附件：\n");
            for (AgentAssistantAttachmentRequest attachment : attachments) {
                builder.append("- 文件名：").append(text(attachment.getFileName()))
                        .append("，类型：").append(text(attachment.getContentType()))
                        .append("，地址：").append(text(attachment.getUrl()))
                        .append("\n");
            }
            builder.append("如果无法直接读取附件正文，请明确说明当前只能看到附件元信息。");
        }
        return builder.toString();
    }

    private void handleRuntimeEvent(
            SseEmitter emitter,
            AgentRuntimeEvent event,
            StringBuilder answer,
            Long[] runId,
            Long[] conversationId,
            boolean[] emittedAnswer) {
        if (event == null) {
            return;
        }
        Map<String, Object> metadata = runtimeMetadata(event);
        runId[0] = readLong(metadata.get("runId"), runId[0]);
        conversationId[0] = readLong(metadata.get("conversationId"), conversationId[0]);
        String type = event.getType() == null ? "" : event.getType().toUpperCase();
        String content = event.getContent();
        if (type.contains("TOOL_CALL_START")) {
            sendRuntimeEvent(emitter, "TOOL_CALL_STARTED", "调用辅助能力", toolProgress(event), metadata, runtimeIds(runId[0], conversationId[0]));
            return;
        }
        if (type.contains("TOOL_RESULT_END")) {
            sendRuntimeEvent(emitter, "TOOL_RESULT_FINISHED", "辅助资料完成", "辅助能力已完成", metadata, runtimeIds(runId[0], conversationId[0]));
            return;
        }
        if (type.contains("TOOL_CALL") || type.contains("TOOL_RESULT")) {
            return;
        }
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (type.contains("TEXT_BLOCK_DELTA")) {
            emittedAnswer[0] = true;
            answer.append(content);
            sendRuntimeEvent(emitter, "ANSWER_DELTA", "回答增量", content, metadata, runtimeIds(runId[0], conversationId[0]));
            return;
        }
        if (type.contains("AGENT_RESULT") || type.contains("TEXT_BLOCK_END")) {
            if (!emittedAnswer[0]) {
                answer.append(content);
                sendRuntimeEvent(emitter, "ANSWER_FINISHED", "回答完成", content, metadata, runtimeIds(runId[0], conversationId[0]));
            } else {
                sendRuntimeEvent(emitter, "ANSWER_FINISHED", "回答完成", "", metadata, runtimeIds(runId[0], conversationId[0]));
            }
        }
    }

    private List<AgentAssistantMessageResponse> recentMessages(Long tenantId, Long userId, Long conversationId) {
        if (conversationId == null) {
            return new ArrayList<AgentAssistantMessageResponse>();
        }
        AgentAssistantMessagesRequest request = new AgentAssistantMessagesRequest();
        request.setConversationId(conversationId);
        List<AgentAssistantMessageResponse> messages = messages(tenantId, userId, request);
        if (messages.size() <= 8) {
            return messages;
        }
        return messages.subList(messages.size() - 8, messages.size());
    }

    private AgentAssistantMessageResponse userMessage(AgentRunEntity run) {
        AgentAssistantMessageResponse response = new AgentAssistantMessageResponse();
        response.setRunId(run.getId());
        response.setRole("user");
        response.setContent(resolveOriginalUserMessage(run));
        response.setStatus(run.getStatus());
        response.setCreatedAt(run.getCreatedAt());
        response.setAttachments(resolveAttachments(run));
        return response;
    }

    private AgentAssistantMessageResponse assistantMessage(AgentRunEntity run) {
        AgentAssistantMessageResponse response = new AgentAssistantMessageResponse();
        response.setRunId(run.getId());
        response.setRole("assistant");
        if ("FAILED".equals(run.getStatus()) && !StringUtils.hasText(run.getOutputText())) {
            response.setContent("运行失败：" + text(run.getErrorMessage()));
        } else if ("STOPPED".equals(run.getStatus()) && !StringUtils.hasText(run.getOutputText())) {
            response.setContent("本次回答已终止。");
        } else {
            response.setContent(run.getOutputText());
        }
        response.setStatus(run.getStatus());
        response.setCreatedAt(run.getFinishedAt() == null ? run.getUpdatedAt() : run.getFinishedAt());
        return response;
    }

    private AgentAssistantAgentResponse toAgentResponse(Long tenantId, Long userId, AgentEntity agent) {
        AgentAssistantAgentResponse response = new AgentAssistantAgentResponse();
        response.setId(agent.getId());
        response.setCode(agent.getCode());
        response.setSceneCode(agent.getSceneCode());
        response.setSceneName(agent.getSceneName());
        response.setName(agent.getName());
        response.setDescription(agent.getDescription());
        response.setModelProvider(agent.getModelProvider());
        response.setModelName(agent.getModelName());
        response.setMaxIters(agent.getMaxIters());
        response.setEnabled(agent.isEnabled());
        response.setConversationCount(countConversations(tenantId, userId, agent.getId()));
        response.setLastMessageAt(lastMessageAt(tenantId, userId, agent.getId()));
        return response;
    }

    private AgentAssistantConversationResponse toConversationResponse(ConversationEntity conversation) {
        AgentAssistantConversationResponse response = new AgentAssistantConversationResponse();
        response.setId(conversation.getId());
        response.setAgentId(conversation.getAgentId());
        response.setTitle(conversation.getTitle());
        response.setSceneCode(conversation.getSceneCode());
        response.setStatus(conversation.getStatus());
        response.setLastMessageAt(conversation.getLastMessageAt());
        response.setCreatedAt(conversation.getCreatedAt());
        return response;
    }

    private ConversationEntity requireConversation(Long tenantId, Long userId, Long conversationId, Long agentId) {
        LambdaQueryWrapper<ConversationEntity> wrapper = Wrappers.<ConversationEntity>lambdaQuery()
                .eq(ConversationEntity::getTenantId, tenantId)
                .eq(ConversationEntity::getUserId, userId)
                .eq(ConversationEntity::getId, conversationId)
                .eq(ConversationEntity::isDeleted, false);
        if (agentId != null) {
            wrapper.eq(ConversationEntity::getAgentId, agentId);
        }
        wrapper.last("limit 1");
        ConversationEntity conversation = conversationMapper.selectOne(wrapper);
        if (conversation == null) {
            throw new BusinessException("AGENT_ASSISTANT_001", "会话不存在或无权访问");
        }
        return conversation;
    }

    private AgentEntity requireEnabledAgent(Long tenantId, Long agentId) {
        AgentEntity agent = agentMapper.selectOne(Wrappers.<AgentEntity>lambdaQuery()
                .eq(AgentEntity::getTenantId, tenantId)
                .eq(AgentEntity::getId, agentId)
                .eq(AgentEntity::isDeleted, false)
                .last("limit 1"));
        if (agent == null) {
            throw new BusinessException("AGENT_ASSISTANT_002", "智能体不存在");
        }
        if (!agent.isEnabled()) {
            throw new BusinessException("AGENT_ASSISTANT_003", "智能体已停用");
        }
        return agent;
    }

    private Long countConversations(Long tenantId, Long userId, Long agentId) {
        return conversationMapper.selectCount(Wrappers.<ConversationEntity>lambdaQuery()
                .eq(ConversationEntity::getTenantId, tenantId)
                .eq(ConversationEntity::getUserId, userId)
                .eq(ConversationEntity::getAgentId, agentId)
                .eq(ConversationEntity::isDeleted, false));
    }

    private LocalDateTime lastMessageAt(Long tenantId, Long userId, Long agentId) {
        ConversationEntity conversation = conversationMapper.selectOne(Wrappers.<ConversationEntity>lambdaQuery()
                .eq(ConversationEntity::getTenantId, tenantId)
                .eq(ConversationEntity::getUserId, userId)
                .eq(ConversationEntity::getAgentId, agentId)
                .eq(ConversationEntity::isDeleted, false)
                .orderByDesc(ConversationEntity::getLastMessageAt)
                .last("limit 1"));
        return conversation == null ? null : conversation.getLastMessageAt();
    }

    private String resolveSessionId(Long userId, Long agentId, AgentAssistantRunRequest request) {
        if (request.getConversationId() != null) {
            return "conversation-" + request.getConversationId();
        }
        return "agent-assistant-" + userId + "-" + agentId + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveOriginalUserMessage(AgentRunEntity run) {
        Map<String, Object> context = parseContext(run.getInputJson());
        Object userMessage = context.get("userMessage");
        if (StringUtils.hasText(userMessage == null ? null : String.valueOf(userMessage))) {
            return String.valueOf(userMessage);
        }
        return run.getInputText();
    }

    private List<AgentAssistantAttachmentRequest> resolveAttachments(AgentRunEntity run) {
        Map<String, Object> context = parseContext(run.getInputJson());
        Object value = context.get("attachments");
        if (value == null) {
            return new ArrayList<AgentAssistantAttachmentRequest>();
        }
        try {
            return Jsons.parseObject(Jsons.toJson(value), new TypeReference<List<AgentAssistantAttachmentRequest>>() {
            });
        } catch (RuntimeException ex) {
            return new ArrayList<AgentAssistantAttachmentRequest>();
        }
    }

    private Map<String, Object> parseContext(String inputJson) {
        try {
            Map<String, Object> context = Jsons.parseObject(inputJson, new TypeReference<Map<String, Object>>() {
            });
            return context == null ? new LinkedHashMap<String, Object>() : context;
        } catch (RuntimeException ex) {
            return new LinkedHashMap<String, Object>();
        }
    }

    private List<AgentAssistantAttachmentRequest> safeAttachments(List<AgentAssistantAttachmentRequest> attachments) {
        if (attachments == null) {
            return new ArrayList<AgentAssistantAttachmentRequest>();
        }
        return attachments;
    }

    private String title(String value) {
        String text = text(value).replaceAll("\\s+", " ").trim();
        if (text.length() <= 24) {
            return text;
        }
        return text.substring(0, 24) + "…";
    }

    private Map<String, Object> runtimeMetadata(AgentRuntimeEvent event) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("rawType", event.getType());
        if (StringUtils.hasText(event.getToolName())) {
            metadata.put("toolName", event.getToolName());
        }
        if (event.getMetadata() != null) {
            metadata.putAll(event.getMetadata());
        }
        return metadata;
    }

    private Map<String, Object> runtimeIds(Long runId, Long conversationId) {
        Map<String, Object> ids = new LinkedHashMap<String, Object>();
        ids.put("runId", runId);
        ids.put("conversationId", conversationId);
        return ids;
    }

    private void sendRuntimeEvent(
            SseEmitter emitter,
            String type,
            String stage,
            String content,
            Map<String, Object> metadata,
            Map<String, Object> ids) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("type", type);
        payload.put("stage", stage);
        payload.put("content", content);
        payload.put("metadata", metadata == null ? new LinkedHashMap<String, Object>() : metadata);
        if (ids != null) {
            payload.putAll(ids);
        }
        payload.put("createdAt", LocalDateTime.now().toString());
        try {
            emitter.send(SseEmitter.event().name(type).data(Jsons.toJson(payload)));
        } catch (IOException ex) {
            throw new IllegalStateException("智能体消息推送失败", ex);
        }
    }

    private String toolProgress(AgentRuntimeEvent event) {
        if (!StringUtils.hasText(event.getToolName())) {
            return "正在调用辅助能力";
        }
        return "正在调用：" + event.getToolName();
    }

    private Long readLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String activeRunKey(Long tenantId, Long userId, String requestId) {
        return tenantId + ":" + userId + ":" + requestId.trim();
    }

    private static class ActiveRun {

        private final AtomicBoolean stopRequested = new AtomicBoolean(false);

        private final AtomicBoolean finished = new AtomicBoolean(false);

        private volatile Subscription subscription;

        public void bind(Subscription value) {
            subscription = value;
            if (stopRequested.get() && value != null) {
                value.cancel();
            }
        }

        public boolean stop() {
            if (!finished.compareAndSet(false, true)) {
                return false;
            }
            stopRequested.set(true);
            Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
            return true;
        }

        public boolean finish() {
            return finished.compareAndSet(false, true);
        }
    }
}
