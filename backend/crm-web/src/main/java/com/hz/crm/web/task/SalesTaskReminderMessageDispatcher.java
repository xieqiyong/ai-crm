package com.hz.crm.web.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.auth.dto.notification.NotificationSendRequest;
import com.hz.crm.auth.service.NotificationService;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.message.LocalMessageEntity;
import com.hz.crm.domain.message.mapper.LocalMessageMapper;
import com.hz.crm.domain.task.SalesTaskEntity;
import com.hz.crm.domain.task.SalesTaskStatus;
import com.hz.crm.domain.task.mapper.SalesTaskMapper;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(
        prefix = "crm.task.reminder",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SalesTaskReminderMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SalesTaskReminderMessageDispatcher.class);

    private static final String TASK_REMINDER_MESSAGE_TYPE = "TASK_REMINDER";

    private static final String SALES_TASK_BUSINESS_TYPE = "SALES_TASK";

    private static final String MESSAGE_STATUS_PENDING = "PENDING";

    private static final String MESSAGE_STATUS_PROCESSING = "PROCESSING";

    private static final String MESSAGE_STATUS_SENT = "SENT";

    private static final String MESSAGE_STATUS_FAILED = "FAILED";

    private static final String MESSAGE_STATUS_CANCELLED = "CANCELLED";

    @Autowired
    private LocalMessageMapper localMessageMapper;

    @Autowired
    private SalesTaskMapper salesTaskMapper;

    @Autowired
    private NotificationService notificationService;

    @Value("${crm.task.reminder.batch-size:50}")
    private int batchSize;

    @Value("${crm.task.reminder.max-retry:3}")
    private int maxRetry;

    @Value("${crm.task.reminder.retry-delay-seconds:60}")
    private long retryDelaySeconds;

    private final String processorId = ManagementFactory.getRuntimeMXBean().getName();

    @Scheduled(
            fixedDelayString = "${crm.task.reminder.dispatch-delay-ms:30000}",
            initialDelayString = "${crm.task.reminder.initial-delay-ms:10000}")
    @Transactional
    public void dispatch() {
        LocalDateTime now = DateTimes.now();
        List<LocalMessageEntity> messages = localMessageMapper.selectList(buildDueMessageQuery(now));
        for (LocalMessageEntity message : messages) {
            dispatchOne(message);
        }
    }

    private LambdaQueryWrapper<LocalMessageEntity> buildDueMessageQuery(LocalDateTime now) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 200));
        return Wrappers.<LocalMessageEntity>lambdaQuery()
                .eq(LocalMessageEntity::isDeleted, false)
                .eq(LocalMessageEntity::getMessageType, TASK_REMINDER_MESSAGE_TYPE)
                .eq(LocalMessageEntity::getBusinessType, SALES_TASK_BUSINESS_TYPE)
                .le(LocalMessageEntity::getSendAt, now)
                .and(wrapper -> wrapper
                        .eq(LocalMessageEntity::getStatus, MESSAGE_STATUS_PENDING)
                        .or(value -> value
                                .eq(LocalMessageEntity::getStatus, MESSAGE_STATUS_FAILED)
                                .lt(LocalMessageEntity::getRetryCount, maxRetry)
                                .isNotNull(LocalMessageEntity::getNextRetryAt)
                                .le(LocalMessageEntity::getNextRetryAt, now)))
                .orderByAsc(LocalMessageEntity::getSendAt)
                .orderByAsc(LocalMessageEntity::getId)
                .last("limit " + safeBatchSize);
    }

    private void dispatchOne(LocalMessageEntity message) {
        if (!claim(message)) {
            return;
        }
        try {
            SalesTaskEntity task = findTask(message.getTenantId(), message.getBusinessId());
            if (!canSend(task)) {
                markCancelled(message);
                return;
            }
            LocalDateTime now = DateTimes.now();
            if (task.getReminderAt().isAfter(now)) {
                reschedule(message, task);
                return;
            }
            sendNotification(message, task);
            markSent(message);
        } catch (RuntimeException ex) {
            markFailed(message, ex);
            log.warn("销售任务提醒发送失败，messageId={}，taskId={}", message.getId(), message.getBusinessId(), ex);
        }
    }

    private boolean claim(LocalMessageEntity message) {
        LocalDateTime now = DateTimes.now();
        int affected = localMessageMapper.update(null, Wrappers.<LocalMessageEntity>lambdaUpdate()
                .eq(LocalMessageEntity::getId, message.getId())
                .eq(LocalMessageEntity::getTenantId, message.getTenantId())
                .eq(LocalMessageEntity::isDeleted, false)
                .in(LocalMessageEntity::getStatus, MESSAGE_STATUS_PENDING, MESSAGE_STATUS_FAILED)
                .set(LocalMessageEntity::getStatus, MESSAGE_STATUS_PROCESSING)
                .set(LocalMessageEntity::getLockedAt, now)
                .set(LocalMessageEntity::getLockedBy, processorId)
                .set(LocalMessageEntity::getUpdatedAt, now));
        return affected == 1;
    }

    private SalesTaskEntity findTask(Long tenantId, Long taskId) {
        if (tenantId == null || taskId == null) {
            return null;
        }
        return salesTaskMapper.selectOne(Wrappers.<SalesTaskEntity>lambdaQuery()
                .eq(SalesTaskEntity::getId, taskId)
                .eq(SalesTaskEntity::getTenantId, tenantId)
                .eq(SalesTaskEntity::isDeleted, false));
    }

    private boolean canSend(SalesTaskEntity task) {
        if (task == null || task.getOwnerId() == null || task.getReminderAt() == null) {
            return false;
        }
        return SalesTaskStatus.PENDING == task.getStatus()
                || SalesTaskStatus.IN_PROGRESS == task.getStatus()
                || SalesTaskStatus.OVERDUE == task.getStatus();
    }

    private void sendNotification(LocalMessageEntity message, SalesTaskEntity task) {
        NotificationSendRequest request = new NotificationSendRequest();
        request.setTitle(message.getTitle());
        request.setContent(message.getContent());
        request.setLevel(message.getLevel());
        request.setTargetType("USER");
        request.setTargetUserId(task.getOwnerId());
        notificationService.send(task.getTenantId(), resolveSenderId(task), request);
    }

    private Long resolveSenderId(SalesTaskEntity task) {
        if (task.getCreatorId() != null) {
            return task.getCreatorId();
        }
        return task.getOwnerId();
    }

    private void markSent(LocalMessageEntity message) {
        LocalDateTime now = DateTimes.now();
        localMessageMapper.update(null, Wrappers.<LocalMessageEntity>lambdaUpdate()
                .eq(LocalMessageEntity::getId, message.getId())
                .eq(LocalMessageEntity::getStatus, MESSAGE_STATUS_PROCESSING)
                .set(LocalMessageEntity::getStatus, MESSAGE_STATUS_SENT)
                .set(LocalMessageEntity::getSentAt, now)
                .set(LocalMessageEntity::getErrorMessage, null)
                .set(LocalMessageEntity::getUpdatedAt, now));
    }

    private void markCancelled(LocalMessageEntity message) {
        localMessageMapper.update(null, Wrappers.<LocalMessageEntity>lambdaUpdate()
                .eq(LocalMessageEntity::getId, message.getId())
                .set(LocalMessageEntity::getStatus, MESSAGE_STATUS_CANCELLED)
                .set(LocalMessageEntity::getUpdatedAt, DateTimes.now()));
    }

    private void reschedule(LocalMessageEntity message, SalesTaskEntity task) {
        localMessageMapper.update(null, Wrappers.<LocalMessageEntity>lambdaUpdate()
                .eq(LocalMessageEntity::getId, message.getId())
                .set(LocalMessageEntity::getTargetUserId, task.getOwnerId())
                .set(LocalMessageEntity::getSendAt, task.getReminderAt())
                .set(LocalMessageEntity::getStatus, MESSAGE_STATUS_PENDING)
                .set(LocalMessageEntity::getLockedAt, null)
                .set(LocalMessageEntity::getLockedBy, null)
                .set(LocalMessageEntity::getErrorMessage, null)
                .set(LocalMessageEntity::getUpdatedAt, DateTimes.now()));
    }

    private void markFailed(LocalMessageEntity message, RuntimeException ex) {
        int retryCount = safeRetryCount(message) + 1;
        LocalDateTime now = DateTimes.now();
        LocalDateTime nextRetryAt = retryCount >= maxRetry ? null : now.plusSeconds(Math.max(5L, retryDelaySeconds));
        localMessageMapper.update(null, Wrappers.<LocalMessageEntity>lambdaUpdate()
                .eq(LocalMessageEntity::getId, message.getId())
                .set(LocalMessageEntity::getStatus, MESSAGE_STATUS_FAILED)
                .set(LocalMessageEntity::getRetryCount, Integer.valueOf(retryCount))
                .set(LocalMessageEntity::getNextRetryAt, nextRetryAt)
                .set(LocalMessageEntity::getErrorMessage, shrink(ex.getMessage(), 1000))
                .set(LocalMessageEntity::getUpdatedAt, now));
    }

    private int safeRetryCount(LocalMessageEntity message) {
        if (message.getRetryCount() == null) {
            return 0;
        }
        return message.getRetryCount().intValue();
    }

    private String shrink(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
