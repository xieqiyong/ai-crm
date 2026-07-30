package com.hz.crm.auth.security;

import com.hz.crm.common.redis.RedisCacheService;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.redisson.api.RSetCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginSessionService {

    private static final String KEY_PREFIX = "crm:auth:session:";

    private static final String USER_SESSION_PREFIX = "crm:auth:user-sessions:";

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Autowired
    private JwtProperties jwtProperties;

    public String nextSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public void save(JwtPrincipal principal, String token) {
        if (!redisCacheService.enabled()) {
            return;
        }
        long ttlSeconds = resolveTtlSeconds(principal);
        redisCacheService.setString(resolveKey(principal), token, ttlSeconds);
        sessionSet(principal).add(principal.getSessionId(), ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean validate(JwtPrincipal principal, String token) {
        if (!redisCacheService.enabled()) {
            return true;
        }
        if (principal == null || isBlank(principal.getSessionId())) {
            return false;
        }
        String storedToken = redisCacheService.getString(resolveKey(principal));
        if (!token.equals(storedToken)) {
            return false;
        }
        long ttlSeconds = resolveTtlSeconds(principal);
        redisCacheService.expire(resolveKey(principal), ttlSeconds);
        sessionSet(principal).add(principal.getSessionId(), ttlSeconds, TimeUnit.SECONDS);
        return true;
    }

    public void remove(JwtPrincipal principal) {
        if (!redisCacheService.enabled() || principal == null || isBlank(principal.getSessionId())) {
            return;
        }
        redisCacheService.delete(resolveKey(principal));
        sessionSet(principal).remove(principal.getSessionId());
    }

    public void revokeOtherSessions(JwtPrincipal principal) {
        if (!redisCacheService.enabled() || principal == null || redissonClient == null) {
            return;
        }
        RSetCache<String> sessions = sessionSet(principal);
        Set<String> sessionIds = sessions.readAll();
        for (String sessionId : new ArrayList<String>(sessionIds)) {
            if (principal.getSessionId().equals(sessionId)) {
                continue;
            }
            redisCacheService.delete(resolveKey(principal.getTenantId(), sessionId));
            sessions.remove(sessionId);
        }
    }

    public void revokeAllSessions(Long tenantId, Long userId) {
        if (!redisCacheService.enabled() || tenantId == null || userId == null || redissonClient == null) {
            return;
        }
        RSetCache<String> sessions = redissonClient.getSetCache(resolveUserSessionKey(tenantId, userId));
        Set<String> sessionIds = sessions.readAll();
        for (String sessionId : new ArrayList<String>(sessionIds)) {
            redisCacheService.delete(resolveKey(tenantId, sessionId));
        }
        sessions.delete();
    }

    public long configuredTtlSeconds() {
        return jwtProperties.getTtlSeconds();
    }

    private String resolveKey(JwtPrincipal principal) {
        return resolveKey(principal.getTenantId(), principal.getSessionId());
    }

    private String resolveKey(Long tenantId, String sessionId) {
        return KEY_PREFIX + tenantId + ":" + sessionId;
    }

    private RSetCache<String> sessionSet(JwtPrincipal principal) {
        return redissonClient.getSetCache(resolveUserSessionKey(principal.getTenantId(), principal.getUserId()));
    }

    private String resolveUserSessionKey(Long tenantId, Long userId) {
        return USER_SESSION_PREFIX + tenantId + ":" + userId;
    }

    private long resolveTtlSeconds(JwtPrincipal principal) {
        long configuredTtl = jwtProperties.getTtlSeconds();
        Long expiresAt = principal.getExpiresAt();
        if (expiresAt == null) {
            return configuredTtl;
        }
        long remainingSeconds = (expiresAt.longValue() - System.currentTimeMillis()) / 1000L;
        if (remainingSeconds < 1L) {
            return 1L;
        }
        if (remainingSeconds > configuredTtl) {
            return configuredTtl;
        }
        return remainingSeconds;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
