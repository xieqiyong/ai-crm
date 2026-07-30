package com.hz.crm.wecom.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.wecom.WecomCorpConfigEntity;
import com.hz.crm.domain.wecom.WecomSyncStatus;
import com.hz.crm.domain.wecom.WecomSyncTaskEntity;
import com.hz.crm.domain.wecom.WecomSyncTrigger;
import com.hz.crm.domain.wecom.mapper.WecomSyncTaskMapper;
import com.hz.crm.wecom.dto.WecomSyncTaskResponse;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class WecomSyncCoordinator {

    @Autowired
    private WecomManageService manageService;

    @Autowired
    private WecomSyncTaskMapper taskMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private WecomTokenService tokenService;

    @Autowired
    private WecomSyncService syncService;

    @Autowired
    @Qualifier("wecomSyncExecutor")
    private TaskExecutor taskExecutor;

    public WecomSyncTaskResponse startManual(Long tenantId, Long operatorId, Long configId) {
        WecomCorpConfigEntity config = manageService.findConfig(tenantId, configId);
        return start(config, operatorId, WecomSyncTrigger.MANUAL);
    }

    public WecomSyncTaskResponse startScheduled(WecomCorpConfigEntity config) {
        return start(config, null, WecomSyncTrigger.SCHEDULED);
    }

    private WecomSyncTaskResponse start(
            WecomCorpConfigEntity config, Long operatorId, WecomSyncTrigger triggerType) {
        RLock lock = tokenService.dispatchLock(config.getTenantId(), config.getId());
        lock.lock(10, TimeUnit.SECONDS);
        try {
            WecomSyncTaskEntity active = findActiveTask(config.getTenantId(), config.getId());
            if (active != null) {
                return manageService.toTaskResponse(active);
            }
            LocalDateTime now = DateTimes.now();
            WecomSyncTaskEntity task = new WecomSyncTaskEntity();
            task.setId(snowflakeIdGenerator.nextId());
            task.setTenantId(config.getTenantId());
            task.setConfigId(config.getId());
            task.setOperatorId(operatorId);
            task.setTriggerType(triggerType);
            task.setStatus(WecomSyncStatus.PENDING);
            task.setDeleted(false);
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            taskMapper.insert(task);
            final Long taskId = task.getId();
            taskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    syncService.execute(taskId);
                }
            });
            return manageService.toTaskResponse(task);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private WecomSyncTaskEntity findActiveTask(Long tenantId, Long configId) {
        QueryWrapper<WecomSyncTaskEntity> wrapper = new QueryWrapper<WecomSyncTaskEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("deleted", false);
        wrapper.in("status", WecomSyncStatus.PENDING.name(), WecomSyncStatus.RUNNING.name());
        wrapper.ge("updated_at", DateTimes.now().minusMinutes(35));
        wrapper.orderByDesc("created_at").last("limit 1");
        return taskMapper.selectOne(wrapper);
    }
}
