package com.hz.crm.auth.security;

import com.hz.crm.common.redis.RedisCacheService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginSessionService {

    private static final String KEY_PREFIX = "crm:auth:session:";

    @Autowired
    private RedisCacheService redisCacheService;

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
        redisCacheService.expire(resolveKey(principal), resolveTtlSeconds(principal));
        return true;
    }

    public void remove(JwtPrincipal principal) {
        if (!redisCacheService.enabled() || principal == null || isBlank(principal.getSessionId())) {
            return;
        }
        redisCacheService.delete(resolveKey(principal));
    }

    public long configuredTtlSeconds() {
        return jwtProperties.getTtlSeconds();
    }

    private String resolveKey(JwtPrincipal principal) {
        return KEY_PREFIX + principal.getTenantId() + ":" + principal.getSessionId();
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
