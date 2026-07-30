package com.hz.crm.wecom.service;

import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.wecom.WecomCorpConfigEntity;
import com.hz.crm.wecom.client.WecomApiClient;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WecomTokenService {

    @Autowired
    private WecomApiClient wecomApiClient;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    public String getToken(WecomCorpConfigEntity config) {
        checkRedis();
        String tokenKey = tokenKey(config);
        RBucket<String> bucket = redissonClient.getBucket(tokenKey);
        String cached = bucket.get();
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        RLock lock = redissonClient.getLock(tokenKey + ":lock");
        lock.lock(20, TimeUnit.SECONDS);
        try {
            cached = bucket.get();
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            String token = wecomApiClient.getAccessToken(config.getCorpId(), config.getCorpSecret());
            bucket.set(token, 7000, TimeUnit.SECONDS);
            return token;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void clearToken(Long tenantId, Long configId) {
        if (redissonClient == null || tenantId == null || configId == null) {
            return;
        }
        redissonClient.getBucket("crm:wecom:token:" + tenantId + ":" + configId).delete();
    }

    public RLock syncLock(Long tenantId, Long configId) {
        checkRedis();
        return redissonClient.getLock("crm:wecom:sync:" + tenantId + ":" + configId);
    }

    public RLock dispatchLock(Long tenantId, Long configId) {
        checkRedis();
        return redissonClient.getLock("crm:wecom:dispatch:" + tenantId + ":" + configId);
    }

    private String tokenKey(WecomCorpConfigEntity config) {
        return "crm:wecom:token:" + config.getTenantId() + ":" + config.getId();
    }

    private void checkRedis() {
        if (redissonClient == null) {
            throw new BusinessException("WECOM_REDIS_001", "企业微信同步需要先启用Redis");
        }
    }
}
