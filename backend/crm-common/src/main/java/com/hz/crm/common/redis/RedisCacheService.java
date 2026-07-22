package com.hz.crm.common.redis;

import com.hz.crm.common.exception.BusinessException;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RedisCacheService {

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Value("${crm.redis.enabled:false}")
    private boolean redisEnabled;

    public boolean enabled() {
        return redisEnabled;
    }

    public void setString(String key, String value, long ttlSeconds) {
        RBucket<String> bucket = requiredClient().getBucket(key);
        bucket.set(value, ttlSeconds, TimeUnit.SECONDS);
    }

    public String getString(String key) {
        RBucket<String> bucket = requiredClient().getBucket(key);
        return bucket.get();
    }

    public <T> void setObject(String key, T value, long ttlSeconds) {
        RBucket<T> bucket = requiredClient().getBucket(key);
        bucket.set(value, ttlSeconds, TimeUnit.SECONDS);
    }

    public <T> T getObject(String key) {
        RBucket<T> bucket = requiredClient().getBucket(key);
        return bucket.get();
    }

    public boolean exists(String key) {
        return requiredClient().getBucket(key).isExists();
    }

    public boolean expire(String key, long ttlSeconds) {
        RBucket<String> bucket = requiredClient().getBucket(key);
        return bucket.expire(ttlSeconds, TimeUnit.SECONDS);
    }

    public void delete(String key) {
        requiredClient().getBucket(key).delete();
    }

    public boolean tryLock(String key, long waitSeconds, long leaseSeconds) {
        RLock lock = requiredClient().getLock(key);
        try {
            return lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("REDIS_002", "获取Redis锁被中断");
        }
    }

    public void unlock(String key) {
        RLock lock = requiredClient().getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private RedissonClient requiredClient() {
        if (redissonClient == null) {
            throw new BusinessException("REDIS_001", "Redis客户端未初始化");
        }
        return redissonClient;
    }
}
