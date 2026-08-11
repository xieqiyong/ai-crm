package com.hz.crm.agent.runtime.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.agent.runtime.core.AgentRuntimeEngine;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentEventEntity;
import com.hz.crm.agent.runtime.domain.AgentRunEntity;
import com.hz.crm.agent.runtime.domain.ConversationEntity;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.agent.runtime.mapper.AgentEventMapper;
import com.hz.crm.agent.runtime.mapper.AgentRunMapper;
import com.hz.crm.agent.runtime.mapper.ConversationMapper;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.json.Jsons;
import com.hz.crm.common.time.DateTimes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AgentRuntimeFacade {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeFacade.class);

    @Autowired
    private AgentRuntimeEngine agentRuntimeEngine;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private AgentRunMapper agentRunMapper;

    @Autowired
    private AgentEventMapper agentEventMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private AgentRuntimeSceneService agentRuntimeSceneService;

    @Autowired
    private AgentTokenQuotaService agentTokenQuotaService;

    public Flux<AgentRuntimeEvent> run(AgentRuntimeRequest request) {
        long prepareStart = System.currentTimeMillis();
        validateBase(request);
        long sceneStart = System.currentTimeMillis();
        agentRuntimeSceneService.prepare(request);
        long sceneMs = System.currentTimeMillis() - sceneStart;
        validatePrepared(request);
        long quotaStart = System.currentTimeMillis();
        AgentTokenQuotaService.TokenReservation tokenReservation = agentTokenQuotaService.reserve(request);
        long quotaMs = System.currentTimeMillis() - quotaStart;
        long conversationStart = System.currentTimeMillis();
        ConversationEntity conversation = resolveConversation(request);
        long conversationMs = System.currentTimeMillis() - conversationStart;
        long runStart = System.currentTimeMillis();
        AgentRunEntity run = createRun(request, conversation, tokenReservation);
        long runMs = System.currentTimeMillis() - runStart;
        request.setConversationId(conversation.getId());
        request.setRunId(run.getId());
        AtomicInteger sequence = new AtomicInteger(0);
        AtomicBoolean finished = new AtomicBoolean(false);
        StringBuffer output = new StringBuffer();
        AgentTokenQuotaService.TokenUsageCounter tokenCounter = agentTokenQuotaService.newCounter();
        long runtimeStart = System.currentTimeMillis();
        log.info(
                "Agent运行准备完成，tenantId={}，userId={}，sceneCode={}，runId={}，conversationId={}，场景准备={}ms，额度预占={}ms，会话处理={}ms，运行记录={}ms，准备总耗时={}ms",
                request.getTenantId(),
                request.getUserId(),
                request.getSceneCode(),
                run.getId(),
                conversation.getId(),
                Long.valueOf(sceneMs),
                Long.valueOf(quotaMs),
                Long.valueOf(conversationMs),
                Long.valueOf(runMs),
                Long.valueOf(System.currentTimeMillis() - prepareStart));
        return agentRuntimeEngine
                .run(request)
                .doOnNext(event -> {
                    int sequenceNo = sequence.incrementAndGet();
                    if (sequenceNo == 1) {
                        log.info(
                                "Agent首个事件返回，tenantId={}，userId={}，sceneCode={}，runId={}，耗时={}ms",
                                request.getTenantId(),
                                request.getUserId(),
                                request.getSceneCode(),
                                run.getId(),
                                Long.valueOf(System.currentTimeMillis() - runtimeStart));
                    }
                    saveEvent(request, run, event, sequenceNo, output, tokenCounter);
                })
                .doOnComplete(() -> {
                    if (finished.compareAndSet(false, true)) {
                        finishRunSuccess(
                                request, run, output.toString(), conversation, tokenReservation, tokenCounter);
                        log.info(
                                "Agent运行完成，tenantId={}，userId={}，sceneCode={}，runId={}，事件数={}，总耗时={}ms",
                                request.getTenantId(),
                                request.getUserId(),
                                request.getSceneCode(),
                                run.getId(),
                                Integer.valueOf(sequence.get()),
                                Long.valueOf(System.currentTimeMillis() - prepareStart));
                    }
                })
                .doOnError(error -> {
                    if (finished.compareAndSet(false, true)) {
                        finishRunFailed(request, run, error, conversation, tokenReservation);
                        log.warn(
                                "Agent运行失败，tenantId={}，userId={}，sceneCode={}，runId={}，事件数={}，总耗时={}ms",
                                request.getTenantId(),
                                request.getUserId(),
                                request.getSceneCode(),
                                run.getId(),
                                Integer.valueOf(sequence.get()),
                                Long.valueOf(System.currentTimeMillis() - prepareStart),
                                error);
                    }
                })
                .doOnCancel(() -> {
                    if (finished.compareAndSet(false, true)) {
                        finishRunStopped(
                                request, run, output.toString(), conversation, tokenReservation);
                        log.info(
                                "Agent运行终止，tenantId={}，userId={}，sceneCode={}，runId={}，事件数={}，总耗时={}ms",
                                request.getTenantId(),
                                request.getUserId(),
                                request.getSceneCode(),
                                run.getId(),
                                Integer.valueOf(sequence.get()),
                                Long.valueOf(System.currentTimeMillis() - prepareStart));
                    }
                });
    }

    private ConversationEntity resolveConversation(AgentRuntimeRequest request) {
        if (request.getConversationId() != null) {
            ConversationEntity conversation = conversationMapper.selectOne(Wrappers.<ConversationEntity>lambdaQuery()
                    .eq(ConversationEntity::getTenantId, request.getTenantId())
                    .eq(ConversationEntity::getUserId, request.getUserId())
                    .eq(ConversationEntity::getAgentId, request.getAgent().getId())
                    .eq(ConversationEntity::getId, request.getConversationId())
                    .eq(ConversationEntity::isDeleted, false)
                    .last("limit 1"));
            if (conversation == null) {
                throw new BusinessException("AGENT_CONVERSATION_001", "会话不存在或无权访问");
            }
            touchConversationBeforeRun(request, conversation);
            return conversation;
        }
        String sessionId = resolveSessionId(request);
        ConversationEntity conversation = conversationMapper.selectOne(Wrappers.<ConversationEntity>lambdaQuery()
                .eq(ConversationEntity::getTenantId, request.getTenantId())
                .eq(ConversationEntity::getUserId, request.getUserId())
                .eq(ConversationEntity::getAgentId, request.getAgent().getId())
                .eq(ConversationEntity::getSessionId, sessionId)
                .eq(ConversationEntity::isDeleted, false)
                .orderByDesc(ConversationEntity::getUpdatedAt)
                .last("limit 1"));
        boolean created = conversation == null;
        LocalDateTime now = DateTimes.now();
        if (conversation == null) {
            conversation = new ConversationEntity();
            conversation.setId(snowflakeIdGenerator.nextId());
            conversation.setTenantId(request.getTenantId());
            conversation.setUserId(request.getUserId());
            conversation.setAgentId(request.getAgent().getId());
            conversation.setSessionId(sessionId);
            conversation.setTitle(resolveConversationTitle(request));
            conversation.setStatus("ACTIVE");
            conversation.setCreatedAt(now);
            conversation.setDeleted(false);
        }
        conversation.setSceneCode(trimToNull(request.getSceneCode()));
        conversation.setBusinessType(trimToNull(request.getBusinessType()));
        conversation.setBusinessId(trimToNull(request.getBusinessId()));
        conversation.setContextJson(Jsons.toJson(request.getContext()));
        conversation.setStatus("ACTIVE");
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        if (created) {
            conversationMapper.insert(conversation);
        } else {
            conversationMapper.updateById(conversation);
        }
        return conversation;
    }

    private void touchConversationBeforeRun(AgentRuntimeRequest request, ConversationEntity conversation) {
        LocalDateTime now = DateTimes.now();
        conversation.setAgentId(request.getAgent().getId());
        conversation.setSceneCode(trimToNull(request.getSceneCode()));
        conversation.setBusinessType(trimToNull(request.getBusinessType()));
        conversation.setBusinessId(trimToNull(request.getBusinessId()));
        conversation.setContextJson(Jsons.toJson(request.getContext()));
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
    }

    private AgentRunEntity createRun(
            AgentRuntimeRequest request,
            ConversationEntity conversation,
            AgentTokenQuotaService.TokenReservation tokenReservation) {
        AgentRunEntity run = new AgentRunEntity();
        run.setId(snowflakeIdGenerator.nextId());
        run.setTenantId(request.getTenantId());
        run.setUserId(request.getUserId());
        run.setAgentId(request.getAgent().getId());
        run.setConversationId(conversation.getId());
        run.setSessionId(conversation.getSessionId());
        run.setSceneCode(trimToNull(request.getSceneCode()));
        run.setBusinessType(trimToNull(request.getBusinessType()));
        run.setBusinessId(trimToNull(request.getBusinessId()));
        run.setInputText(request.getMessage());
        run.setInputJson(Jsons.toJson(request.getContext()));
        LocalDateTime now = DateTimes.now();
        run.setStartedAt(now);
        run.setStatus("RUNNING");
        run.setInputTokenCount(tokenReservation.getInputTokenCount());
        run.setOutputTokenCount(0L);
        run.setTotalTokenCount(0L);
        run.setEstimatedTokenCount(0L);
        run.setUsageEstimated(tokenReservation.isUsageEstimated());
        run.setReservedTokenCount(tokenReservation.getReservedTokenCount());
        run.setDailyTokenLimit(tokenReservation.getDailyTokenLimit());
        run.setDeleted(false);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        agentRunMapper.insert(run);
        return run;
    }

    private void saveEvent(
            AgentRuntimeRequest request,
            AgentRunEntity run,
            AgentRuntimeEvent event,
            int sequenceNo,
            StringBuffer output,
            AgentTokenQuotaService.TokenUsageCounter tokenCounter) {
        enrichRuntimeEvent(run, event);
        appendAnswerOutput(output, event);
        agentTokenQuotaService.collectEventUsage(tokenCounter, event);
        AgentEventEntity entity = new AgentEventEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(request.getTenantId());
        entity.setRunId(run.getId());
        entity.setConversationId(run.getConversationId());
        entity.setAgentId(run.getAgentId());
        entity.setSequenceNo(sequenceNo);
        entity.setEventId(event.getId());
        entity.setEventType(blank(event.getType()) ? "UNKNOWN" : event.getType());
        entity.setContent(event.getContent());
        entity.setToolName(event.getToolName());
        entity.setMetadataJson(Jsons.toJson(event.getMetadata()));
        entity.setCreatedAt(DateTimes.now());
        agentEventMapper.insert(entity);
    }

    private void enrichRuntimeEvent(AgentRunEntity run, AgentRuntimeEvent event) {
        if (event == null || run == null || event.getMetadata() == null) {
            return;
        }
        event.getMetadata().put("runId", run.getId());
        event.getMetadata().put("conversationId", run.getConversationId());
    }

    private void finishRunSuccess(
            AgentRuntimeRequest request,
            AgentRunEntity run,
            String output,
            ConversationEntity conversation,
            AgentTokenQuotaService.TokenReservation tokenReservation,
            AgentTokenQuotaService.TokenUsageCounter tokenCounter) {
        AgentTokenQuotaService.TokenUsageSnapshot tokenUsage =
                agentTokenQuotaService.completeSuccess(request, tokenReservation, tokenCounter, output);
        run.setStatus("SUCCESS");
        run.setOutputText(shrink(output, 6000));
        fillTokenUsage(run, tokenUsage);
        finishRun(run);
        touchConversation(conversation);
    }

    private void finishRunFailed(
            AgentRuntimeRequest request,
            AgentRunEntity run,
            Throwable error,
            ConversationEntity conversation,
            AgentTokenQuotaService.TokenReservation tokenReservation) {
        AgentTokenQuotaService.TokenUsageSnapshot tokenUsage =
                agentTokenQuotaService.completeFailed(request, tokenReservation);
        run.setStatus("FAILED");
        run.setErrorMessage(shrink(error == null ? "Agent运行失败" : error.getMessage(), 1000));
        fillTokenUsage(run, tokenUsage);
        finishRun(run);
        touchConversation(conversation);
    }

    private void finishRunStopped(
            AgentRuntimeRequest request,
            AgentRunEntity run,
            String output,
            ConversationEntity conversation,
            AgentTokenQuotaService.TokenReservation tokenReservation) {
        AgentTokenQuotaService.TokenUsageSnapshot tokenUsage =
                agentTokenQuotaService.completeStopped(request, tokenReservation);
        run.setStatus("STOPPED");
        run.setOutputText(shrink(output, 6000));
        fillTokenUsage(run, tokenUsage);
        finishRun(run);
        touchConversation(conversation);
    }

    private void appendAnswerOutput(StringBuffer output, AgentRuntimeEvent event) {
        if (event == null || blank(event.getContent())) {
            return;
        }
        String type = event.getType() == null ? "" : event.getType().toUpperCase();
        if (type.contains("TOOL_RESULT") || type.contains("TOOL_CALL") || type.contains("WORKFLOW")) {
            return;
        }
        if (type.contains("TEXT_BLOCK_DELTA")) {
            output.append(event.getContent());
            return;
        }
        if (type.contains("AGENT_RESULT") && output.length() == 0) {
            output.append(event.getContent());
        }
    }

    private void fillTokenUsage(AgentRunEntity run, AgentTokenQuotaService.TokenUsageSnapshot tokenUsage) {
        run.setInputTokenCount(tokenUsage.getInputTokenCount());
        run.setOutputTokenCount(tokenUsage.getOutputTokenCount());
        run.setTotalTokenCount(tokenUsage.getTotalTokenCount());
        run.setEstimatedTokenCount(tokenUsage.getEstimatedTokenCount());
        run.setUsageEstimated(tokenUsage.isUsageEstimated());
    }

    private void finishRun(AgentRunEntity run) {
        LocalDateTime finishedAt = DateTimes.now();
        run.setFinishedAt(finishedAt);
        if (run.getStartedAt() != null) {
            run.setElapsedMs(Duration.between(run.getStartedAt(), finishedAt).toMillis());
        }
        run.setUpdatedAt(finishedAt);
        agentRunMapper.updateById(run);
    }

    private void touchConversation(ConversationEntity conversation) {
        LocalDateTime now = DateTimes.now();
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
    }

    private String resolveSessionId(AgentRuntimeRequest request) {
        if (!blank(request.getSessionId())) {
            return request.getSessionId().trim();
        }
        String scene = blank(request.getSceneCode()) ? "agent" : request.getSceneCode().trim();
        return scene + "-" + request.getAgent().getId() + "-" + request.getUserId();
    }

    private String resolveConversationTitle(AgentRuntimeRequest request) {
        Object title = request.getContext() == null ? null : request.getContext().get("conversationTitle");
        if (!blank(title == null ? null : String.valueOf(title))) {
            return shrink(String.valueOf(title).trim(), 200);
        }
        if (!blank(request.getBusinessType()) && !blank(request.getBusinessId())) {
            return request.getBusinessType().trim() + "：" + request.getBusinessId().trim();
        }
        return request.getAgent().getName();
    }

    private void validateBase(AgentRuntimeRequest request) {
        if (request == null) {
            throw new BusinessException("AGENT_RUNTIME_001", "运行请求不能为空");
        }
        if (request.getTenantId() == null) {
            throw new BusinessException("AGENT_RUNTIME_002", "租户不能为空");
        }
        if (request.getUserId() == null) {
            throw new BusinessException("AGENT_RUNTIME_003", "用户不能为空");
        }
        if (blank(request.getMessage())) {
            throw new BusinessException("AGENT_RUNTIME_005", "消息内容不能为空");
        }
    }

    private void validatePrepared(AgentRuntimeRequest request) {
        if (request.getAgent() == null) {
            throw new BusinessException("AGENT_RUNTIME_004", "场景智能体不能为空");
        }
        if (blank(request.getSceneCode())) {
            throw new BusinessException("AGENT_RUNTIME_006", "场景编码不能为空");
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private String trimToNull(String value) {
        if (blank(value)) {
            return null;
        }
        return value.trim();
    }

    private String shrink(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
