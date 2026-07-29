package com.hz.crm.agent.runtime.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.domain.AgentTokenQuotaUserEntity;
import com.hz.crm.agent.runtime.domain.AgentTokenUsageEntity;
import com.hz.crm.agent.runtime.dto.AgentTokenUsageTodayResponse;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import com.hz.crm.agent.runtime.mapper.AgentTokenQuotaUserMapper;
import com.hz.crm.agent.runtime.mapper.AgentTokenUsageMapper;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.redis.RedisCacheService;
import com.hz.crm.common.time.DateTimes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTokenQuotaService {

    @Autowired
    private AgentRuntimeProperties agentRuntimeProperties;

    @Autowired
    private AgentRuntimePromptService agentRuntimePromptService;

    @Autowired
    private AgentTokenUsageMapper agentTokenUsageMapper;

    @Autowired
    private AgentTokenQuotaUserMapper agentTokenQuotaUserMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired(required = false)
    private RedisCacheService redisCacheService;

    @Transactional(readOnly = true)
    public AgentTokenUsageTodayResponse today(Long tenantId, Long userId) {
        LocalDate usageDate = DateTimes.now().toLocalDate();
        AgentTokenUsageEntity usage = selectUsage(tenantId, userId, usageDate, false);
        AgentTokenUsageTodayResponse response = new AgentTokenUsageTodayResponse();
        response.setUsageDate(usageDate);
        AgentTokenQuotaUserEntity quota = selectEnabledQuota(tenantId, userId);
        response.setDailyTokenLimit(resolveDailyLimit(quota));
        response.setQuotaSource(quota == null ? "DEFAULT" : "USER");
        if (usage == null) {
            response.setRemainingTokenCount(resolveRemaining(response.getDailyTokenLimit(), 0L, 0L));
            return response;
        }
        response.setInputTokenCount(usage.getInputTokenCount());
        response.setOutputTokenCount(usage.getOutputTokenCount());
        response.setTotalTokenCount(usage.getTotalTokenCount());
        response.setEstimatedTokenCount(usage.getEstimatedTokenCount());
        response.setReservedTokenCount(usage.getReservedTokenCount());
        response.setRequestCount(usage.getRequestCount());
        response.setSuccessCount(usage.getSuccessCount());
        response.setFailedCount(usage.getFailedCount());
        response.setRemainingTokenCount(resolveRemaining(
                response.getDailyTokenLimit(),
                response.getTotalTokenCount(),
                response.getReservedTokenCount()));
        return response;
    }

    @Transactional
    public TokenReservation reserve(AgentRuntimeRequest request) {
        TokenReservation reservation = buildReservation(request);
        return withUsageLock(request, reservation.getUsageDate(), () -> reserveInDatabase(request, reservation));
    }

    @Transactional
    public TokenUsageSnapshot completeSuccess(
            AgentRuntimeRequest request,
            TokenReservation reservation,
            TokenUsageCounter counter,
            String output) {
        TokenUsageSnapshot snapshot = resolveSuccessUsage(reservation, counter, output);
        withUsageLock(request, reservation.getUsageDate(), () -> {
            AgentTokenUsageEntity usage = findOrCreateUsage(request, reservation.getUsageDate());
            usage.setReservedTokenCount(subtract(usage.getReservedTokenCount(), reservation.getReservedTokenCount()));
            usage.setInputTokenCount(add(usage.getInputTokenCount(), snapshot.getInputTokenCount()));
            usage.setOutputTokenCount(add(usage.getOutputTokenCount(), snapshot.getOutputTokenCount()));
            usage.setTotalTokenCount(add(usage.getTotalTokenCount(), snapshot.getTotalTokenCount()));
            usage.setEstimatedTokenCount(add(usage.getEstimatedTokenCount(), snapshot.getEstimatedTokenCount()));
            usage.setSuccessCount(add(usage.getSuccessCount(), 1L));
            saveUsage(usage);
            return snapshot;
        });
        return snapshot;
    }

    @Transactional
    public TokenUsageSnapshot completeFailed(
            AgentRuntimeRequest request,
            TokenReservation reservation) {
        TokenUsageSnapshot snapshot = new TokenUsageSnapshot();
        snapshot.setInputTokenCount(reservation == null ? 0L : reservation.getInputTokenCount());
        snapshot.setOutputTokenCount(0L);
        snapshot.setTotalTokenCount(0L);
        snapshot.setEstimatedTokenCount(0L);
        snapshot.setUsageEstimated(true);
        if (reservation == null) {
            return snapshot;
        }
        withUsageLock(request, reservation.getUsageDate(), () -> {
            AgentTokenUsageEntity usage = findOrCreateUsage(request, reservation.getUsageDate());
            usage.setReservedTokenCount(subtract(usage.getReservedTokenCount(), reservation.getReservedTokenCount()));
            usage.setFailedCount(add(usage.getFailedCount(), 1L));
            saveUsage(usage);
            return snapshot;
        });
        return snapshot;
    }

    @Transactional
    public TokenUsageSnapshot completeStopped(
            AgentRuntimeRequest request,
            TokenReservation reservation) {
        TokenUsageSnapshot snapshot = new TokenUsageSnapshot();
        snapshot.setInputTokenCount(0L);
        snapshot.setOutputTokenCount(0L);
        snapshot.setTotalTokenCount(0L);
        snapshot.setEstimatedTokenCount(0L);
        snapshot.setUsageEstimated(true);
        if (reservation == null) {
            return snapshot;
        }
        withUsageLock(request, reservation.getUsageDate(), () -> {
            AgentTokenUsageEntity usage = findOrCreateUsage(request, reservation.getUsageDate());
            usage.setReservedTokenCount(subtract(usage.getReservedTokenCount(), reservation.getReservedTokenCount()));
            saveUsage(usage);
            return snapshot;
        });
        return snapshot;
    }

    public TokenUsageCounter newCounter() {
        return new TokenUsageCounter();
    }

    public void collectEventUsage(TokenUsageCounter counter, AgentRuntimeEvent event) {
        if (counter == null || event == null || event.getMetadata() == null) {
            return;
        }
        Map<String, Object> metadata = event.getMetadata();
        Long inputTokens = readLong(metadata.get("inputTokens"));
        Long outputTokens = readLong(metadata.get("outputTokens"));
        Long totalTokens = readLong(metadata.get("totalTokens"));
        if (inputTokens != null) {
            counter.setInputTokenCount(max(counter.getInputTokenCount(), inputTokens));
            counter.setUsageEstimated(false);
        }
        if (outputTokens != null) {
            counter.setOutputTokenCount(max(counter.getOutputTokenCount(), outputTokens));
            counter.setUsageEstimated(false);
        }
        if (totalTokens != null) {
            counter.setTotalTokenCount(max(counter.getTotalTokenCount(), totalTokens));
            counter.setUsageEstimated(false);
        }
    }

    private TokenReservation buildReservation(AgentRuntimeRequest request) {
        TokenReservation reservation = new TokenReservation();
        reservation.setUsageDate(DateTimes.now().toLocalDate());
        reservation.setInputTokenCount(estimateInputTokens(request));
        reservation.setDailyTokenLimit(resolveDailyLimit(request.getTenantId(), request.getUserId()));
        reservation.setReservedTokenCount(add(reservation.getInputTokenCount(), resolveReserveOutputTokens()));
        reservation.setUsageEstimated(true);
        return reservation;
    }

    private TokenReservation reserveInDatabase(AgentRuntimeRequest request, TokenReservation reservation) {
        AgentTokenUsageEntity usage = findOrCreateUsage(request, reservation.getUsageDate());
        Long dailyLimit = reservation.getDailyTokenLimit();
        if (dailyLimit != null && dailyLimit.longValue() > 0L) {
            long occupied = add(usage.getTotalTokenCount(), usage.getReservedTokenCount());
            long remaining = dailyLimit.longValue() - occupied;
            if (remaining <= 0L || reservation.getReservedTokenCount() > remaining) {
                throw new BusinessException(
                        "AGENT_TOKEN_001",
                        "今日AI Token额度不足，剩余额度：" + Math.max(remaining, 0L)
                                + "，本次预计需要：" + reservation.getReservedTokenCount());
            }
            reservation.setUsedTokenCount(usage.getTotalTokenCount());
            reservation.setRemainingTokenCount(remaining - reservation.getReservedTokenCount());
        }
        usage.setReservedTokenCount(add(usage.getReservedTokenCount(), reservation.getReservedTokenCount()));
        usage.setRequestCount(add(usage.getRequestCount(), 1L));
        saveUsage(usage);
        return reservation;
    }

    private TokenUsageSnapshot resolveSuccessUsage(
            TokenReservation reservation,
            TokenUsageCounter counter,
            String output) {
        TokenUsageSnapshot snapshot = new TokenUsageSnapshot();
        if (counter != null && !counter.isUsageEstimated()) {
            long inputTokens = counter.getInputTokenCount() == null || counter.getInputTokenCount() <= 0L
                    ? reservation.getInputTokenCount()
                    : counter.getInputTokenCount();
            long totalTokens = counter.getTotalTokenCount() == null || counter.getTotalTokenCount() <= 0L
                    ? add(inputTokens, counter.getOutputTokenCount())
                    : counter.getTotalTokenCount();
            long outputTokens = counter.getOutputTokenCount() == null || counter.getOutputTokenCount() <= 0L
                    ? Math.max(totalTokens - inputTokens, 0L)
                    : counter.getOutputTokenCount();
            snapshot.setInputTokenCount(inputTokens);
            snapshot.setOutputTokenCount(outputTokens);
            snapshot.setTotalTokenCount(totalTokens);
            snapshot.setEstimatedTokenCount(0L);
            snapshot.setUsageEstimated(false);
            return snapshot;
        }
        long inputTokens = reservation.getInputTokenCount();
        long outputTokens = estimateTokens(output);
        long totalTokens = add(inputTokens, outputTokens);
        snapshot.setInputTokenCount(inputTokens);
        snapshot.setOutputTokenCount(outputTokens);
        snapshot.setTotalTokenCount(totalTokens);
        snapshot.setEstimatedTokenCount(totalTokens);
        snapshot.setUsageEstimated(true);
        return snapshot;
    }

    private AgentTokenUsageEntity findOrCreateUsage(AgentRuntimeRequest request, LocalDate usageDate) {
        AgentTokenUsageEntity usage = selectUsage(request.getTenantId(), request.getUserId(), usageDate, true);
        if (usage != null) {
            return usage;
        }
        usage = selectUsage(request.getTenantId(), request.getUserId(), usageDate, false);
        if (usage != null) {
            return usage;
        }
        usage = new AgentTokenUsageEntity();
        usage.setId(snowflakeIdGenerator.nextId());
        usage.setTenantId(request.getTenantId());
        usage.setUserId(request.getUserId());
        usage.setUsageDate(usageDate);
        LocalDateTime now = DateTimes.now();
        usage.setCreatedAt(now);
        usage.setUpdatedAt(now);
        return usage;
    }

    private AgentTokenUsageEntity selectUsage(Long tenantId, Long userId, LocalDate usageDate, boolean forUpdate) {
        return agentTokenUsageMapper.selectOne(Wrappers.<AgentTokenUsageEntity>lambdaQuery()
                .eq(AgentTokenUsageEntity::getTenantId, tenantId)
                .eq(AgentTokenUsageEntity::getUserId, userId)
                .eq(AgentTokenUsageEntity::getUsageDate, usageDate)
                .last(forUpdate ? "limit 1 for update" : "limit 1"));
    }

    private void saveUsage(AgentTokenUsageEntity usage) {
        LocalDateTime now = DateTimes.now();
        if (usage.getCreatedAt() == null) {
            usage.setCreatedAt(now);
        }
        usage.setUpdatedAt(now);
        if (agentTokenUsageMapper.selectById(usage.getId()) == null) {
            agentTokenUsageMapper.insert(usage);
        } else {
            agentTokenUsageMapper.updateById(usage);
        }
    }

    private <T> T withUsageLock(AgentRuntimeRequest request, LocalDate usageDate, Supplier<T> supplier) {
        String lockKey = "crm:agent:token-usage:" + request.getTenantId() + ":" + request.getUserId() + ":" + usageDate;
        boolean locked = false;
        if (redisCacheService != null && redisCacheService.enabled()) {
            locked = redisCacheService.tryLock(lockKey, 3L, 10L);
            if (!locked) {
                throw new BusinessException("AGENT_TOKEN_002", "Token用量正在结算，请稍后重试");
            }
        }
        try {
            return supplier.get();
        } finally {
            if (locked) {
                redisCacheService.unlock(lockKey);
            }
        }
    }

    private long estimateInputTokens(AgentRuntimeRequest request) {
        if (request == null) {
            return 0L;
        }
        String prompt = "";
        if (request.getAgent() != null) {
            prompt = agentRuntimePromptService.render(
                    request.getAgent().getSystemPrompt(),
                    request.getInjectedPrompt(),
                    request.getContext());
        }
        return add(estimateTokens(prompt), estimateTokens(request.getMessage()));
    }

    private long estimateTokens(String text) {
        if (text == null || text.trim().length() == 0) {
            return 0L;
        }
        long tokens = 0L;
        long asciiCount = 0L;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (codePoint < 128) {
                asciiCount++;
            } else {
                tokens++;
            }
        }
        tokens += (asciiCount + 3L) / 4L;
        return tokens;
    }

    public Long resolveDailyLimit(Long tenantId, Long userId) {
        return resolveDailyLimit(selectEnabledQuota(tenantId, userId));
    }

    public Long resolveDefaultDailyLimit() {
        Long value = agentRuntimeProperties.getTokenDailyLimit();
        if (value == null || value.longValue() < 0L) {
            return 0L;
        }
        return value;
    }

    private Long resolveDailyLimit(AgentTokenQuotaUserEntity quota) {
        if (quota != null && quota.getDailyTokenLimit() != null) {
            return Math.max(quota.getDailyTokenLimit(), 0L);
        }
        return resolveDefaultDailyLimit();
    }

    private AgentTokenQuotaUserEntity selectEnabledQuota(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return null;
        }
        return agentTokenQuotaUserMapper.selectOne(Wrappers.<AgentTokenQuotaUserEntity>lambdaQuery()
                .eq(AgentTokenQuotaUserEntity::getTenantId, tenantId)
                .eq(AgentTokenQuotaUserEntity::getUserId, userId)
                .eq(AgentTokenQuotaUserEntity::isDeleted, false)
                .eq(AgentTokenQuotaUserEntity::isEnabled, true)
                .last("limit 1"));
    }

    private Long resolveReserveOutputTokens() {
        Long value = agentRuntimeProperties.getTokenReserveOutputTokens();
        if (value == null || value.longValue() < 0L) {
            return 0L;
        }
        return value;
    }

    private Long readLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Long resolveRemaining(Long dailyLimit, Long totalTokenCount, Long reservedTokenCount) {
        if (dailyLimit == null || dailyLimit.longValue() <= 0L) {
            return 0L;
        }
        return Math.max(dailyLimit.longValue() - add(totalTokenCount, reservedTokenCount), 0L);
    }

    private long max(Long first, Long second) {
        long left = first == null ? 0L : first.longValue();
        long right = second == null ? 0L : second.longValue();
        return Math.max(left, right);
    }

    private long add(Long first, Long second) {
        long left = first == null ? 0L : first.longValue();
        long right = second == null ? 0L : second.longValue();
        return left + right;
    }

    private long subtract(Long first, Long second) {
        long left = first == null ? 0L : first.longValue();
        long right = second == null ? 0L : second.longValue();
        return Math.max(left - right, 0L);
    }

    @Getter
    @Setter
    public static class TokenReservation {

        private LocalDate usageDate;

        private Long inputTokenCount = 0L;

        private Long reservedTokenCount = 0L;

        private Long dailyTokenLimit = 0L;

        private Long usedTokenCount = 0L;

        private Long remainingTokenCount = 0L;

        private boolean usageEstimated = true;
    }

    @Getter
    @Setter
    public static class TokenUsageCounter {

        private Long inputTokenCount = 0L;

        private Long outputTokenCount = 0L;

        private Long totalTokenCount = 0L;

        private boolean usageEstimated = true;
    }

    @Getter
    @Setter
    public static class TokenUsageSnapshot {

        private Long inputTokenCount = 0L;

        private Long outputTokenCount = 0L;

        private Long totalTokenCount = 0L;

        private Long estimatedTokenCount = 0L;

        private boolean usageEstimated = true;
    }
}
