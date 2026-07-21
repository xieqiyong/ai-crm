package com.hz.crm.common.id;

import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1704067200000L;

    private static final long WORKER_ID_BITS = 10L;

    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1L;

    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1L;

    private final long workerId;

    private long lastTimestamp = -1L;

    private long sequence = 0L;

    public SnowflakeIdGenerator() {
        this.workerId = resolveWorkerId();
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("系统时间发生回退，暂时无法生成编号");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1L) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = waitNextMillis(timestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;
    }

    private long waitNextMillis(long timestamp) {
        long current = timestamp;
        while (current <= lastTimestamp) {
            current = System.currentTimeMillis();
        }
        return current;
    }

    private long resolveWorkerId() {
        String value = System.getenv("CRM_WORKER_ID");
        if (value == null || value.trim().length() == 0) {
            return 1L;
        }
        long parsed = Long.parseLong(value);
        if (parsed < 0L || parsed > MAX_WORKER_ID) {
            throw new IllegalArgumentException("工作节点编号超出范围");
        }
        return parsed;
    }
}
