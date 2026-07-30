package com.hz.crm.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.auth.domain.SysNotificationEntity;
import com.hz.crm.auth.domain.SysNotificationReceiptEntity;
import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.dto.notification.NotificationItemResponse;
import com.hz.crm.auth.dto.notification.NotificationQuery;
import com.hz.crm.auth.dto.notification.NotificationSendRequest;
import com.hz.crm.auth.dto.notification.NotificationUnreadResponse;
import com.hz.crm.auth.dto.UserOptionResponse;
import com.hz.crm.auth.mapper.SysNotificationMapper;
import com.hz.crm.auth.mapper.SysNotificationReceiptMapper;
import com.hz.crm.auth.mapper.SysUserMapper;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    @Autowired
    private SysNotificationMapper notificationMapper;

    @Autowired
    private SysNotificationReceiptMapper receiptMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public void send(Long tenantId, Long senderId, NotificationSendRequest request) {
        validateRequest(request);
        String targetType = request.getTargetType().trim().toUpperCase(Locale.ROOT);
        List<SysUserEntity> recipients = resolveRecipients(tenantId, targetType, request.getTargetUserId());
        if (recipients.isEmpty()) {
            throw new BusinessException("NOTIFICATION_004", "没有可接收通知的用户");
        }
        SysNotificationEntity notification = new SysNotificationEntity();
        notification.setId(snowflakeIdGenerator.nextId());
        notification.setTenantId(tenantId);
        notification.setTitle(request.getTitle().trim());
        notification.setContent(request.getContent().trim());
        notification.setLevel(normalizeLevel(request.getLevel()));
        notification.setTargetType(targetType);
        notification.setTargetUserId("USER".equals(targetType) ? request.getTargetUserId() : null);
        notification.setSenderId(senderId);
        notification.setCreatedAt(DateTimes.now());
        notificationMapper.insert(notification);
        for (SysUserEntity recipient : recipients) {
            SysNotificationReceiptEntity receipt = new SysNotificationReceiptEntity();
            receipt.setId(snowflakeIdGenerator.nextId());
            receipt.setTenantId(tenantId);
            receipt.setNotificationId(notification.getId());
            receipt.setUserId(recipient.getId());
            receipt.setCreatedAt(DateTimes.now());
            receiptMapper.insert(receipt);
        }
    }

    public PageData<NotificationItemResponse> page(
            Long tenantId, Long userId, NotificationQuery query) {
        NotificationQuery safeQuery = query == null ? new NotificationQuery() : query;
        LambdaQueryWrapper<SysNotificationReceiptEntity> countWrapper =
                Wrappers.<SysNotificationReceiptEntity>lambdaQuery()
                        .eq(SysNotificationReceiptEntity::getTenantId, tenantId)
                        .eq(SysNotificationReceiptEntity::getUserId, userId);
        Long total = receiptMapper.selectCount(countWrapper);
        LambdaQueryWrapper<SysNotificationReceiptEntity> wrapper =
                Wrappers.<SysNotificationReceiptEntity>lambdaQuery()
                        .eq(SysNotificationReceiptEntity::getTenantId, tenantId)
                        .eq(SysNotificationReceiptEntity::getUserId, userId)
                        .orderByDesc(SysNotificationReceiptEntity::getCreatedAt);
        int offset = (safeQuery.safePageNo() - 1) * safeQuery.safePageSize();
        wrapper.last("limit " + safeQuery.safePageSize() + " offset " + offset);
        List<SysNotificationReceiptEntity> receipts = receiptMapper.selectList(wrapper);
        List<NotificationItemResponse> records = toResponses(tenantId, receipts);
        return PageData.of(
                total == null ? 0L : total.longValue(),
                safeQuery.safePageNo(),
                safeQuery.safePageSize(),
                records);
    }

    public NotificationUnreadResponse unreadCount(Long tenantId, Long userId) {
        Long count = receiptMapper.selectCount(Wrappers.<SysNotificationReceiptEntity>lambdaQuery()
                .eq(SysNotificationReceiptEntity::getTenantId, tenantId)
                .eq(SysNotificationReceiptEntity::getUserId, userId)
                .isNull(SysNotificationReceiptEntity::getReadAt));
        NotificationUnreadResponse response = new NotificationUnreadResponse();
        response.setUnreadCount(count == null ? 0L : count.longValue());
        return response;
    }

    public List<UserOptionResponse> recipients(Long tenantId) {
        List<SysUserEntity> users = userMapper.selectList(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::isDeleted, false)
                .eq(SysUserEntity::isEnabled, true)
                .orderByAsc(SysUserEntity::getDisplayName)
                .orderByAsc(SysUserEntity::getUsername));
        List<UserOptionResponse> responses = new ArrayList<UserOptionResponse>();
        for (SysUserEntity user : users) {
            UserOptionResponse response = new UserOptionResponse();
            response.setId(user.getId());
            response.setUsername(user.getUsername());
            response.setDisplayName(user.getDisplayName());
            response.setName(trimToNull(user.getDisplayName()) == null
                    ? user.getUsername()
                    : user.getDisplayName().trim());
            responses.add(response);
        }
        return responses;
    }

    @Transactional
    public void read(Long tenantId, Long userId, Long receiptId) {
        SysNotificationReceiptEntity receipt = findReceipt(tenantId, userId, receiptId);
        if (receipt.getReadAt() == null) {
            receipt.setReadAt(DateTimes.now());
            receiptMapper.updateById(receipt);
        }
    }

    @Transactional
    public void readAll(Long tenantId, Long userId) {
        List<SysNotificationReceiptEntity> receipts = receiptMapper.selectList(
                Wrappers.<SysNotificationReceiptEntity>lambdaQuery()
                        .eq(SysNotificationReceiptEntity::getTenantId, tenantId)
                        .eq(SysNotificationReceiptEntity::getUserId, userId)
                        .isNull(SysNotificationReceiptEntity::getReadAt));
        for (SysNotificationReceiptEntity receipt : receipts) {
            receipt.setReadAt(DateTimes.now());
            receiptMapper.updateById(receipt);
        }
    }

    private List<NotificationItemResponse> toResponses(
            Long tenantId, List<SysNotificationReceiptEntity> receipts) {
        List<Long> notificationIds = new ArrayList<Long>();
        for (SysNotificationReceiptEntity receipt : receipts) {
            notificationIds.add(receipt.getNotificationId());
        }
        Map<Long, SysNotificationEntity> notificationMap = new HashMap<Long, SysNotificationEntity>();
        if (!notificationIds.isEmpty()) {
            List<SysNotificationEntity> notifications = notificationMapper.selectBatchIds(notificationIds);
            for (SysNotificationEntity notification : notifications) {
                if (tenantId.equals(notification.getTenantId())) {
                    notificationMap.put(notification.getId(), notification);
                }
            }
        }
        Map<Long, String> senderNames = resolveSenderNames(tenantId, notificationMap);
        List<NotificationItemResponse> responses = new ArrayList<NotificationItemResponse>();
        for (SysNotificationReceiptEntity receipt : receipts) {
            SysNotificationEntity notification = notificationMap.get(receipt.getNotificationId());
            if (notification == null) {
                continue;
            }
            NotificationItemResponse response = new NotificationItemResponse();
            response.setId(receipt.getId());
            response.setTitle(notification.getTitle());
            response.setContent(notification.getContent());
            response.setLevel(notification.getLevel());
            response.setTargetType(notification.getTargetType());
            response.setSenderName(senderNames.get(notification.getSenderId()));
            response.setCreatedAt(notification.getCreatedAt());
            response.setReadAt(receipt.getReadAt());
            responses.add(response);
        }
        return responses;
    }

    private Map<Long, String> resolveSenderNames(
            Long tenantId, Map<Long, SysNotificationEntity> notifications) {
        List<Long> senderIds = new ArrayList<Long>();
        for (SysNotificationEntity notification : notifications.values()) {
            if (!senderIds.contains(notification.getSenderId())) {
                senderIds.add(notification.getSenderId());
            }
        }
        Map<Long, String> names = new HashMap<Long, String>();
        if (senderIds.isEmpty()) {
            return names;
        }
        List<SysUserEntity> users = userMapper.selectList(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getTenantId, tenantId)
                .in(SysUserEntity::getId, senderIds));
        for (SysUserEntity user : users) {
            String name = trimToNull(user.getDisplayName());
            names.put(user.getId(), name == null ? user.getUsername() : name);
        }
        return names;
    }

    private List<SysUserEntity> resolveRecipients(Long tenantId, String targetType, Long targetUserId) {
        LambdaQueryWrapper<SysUserEntity> wrapper = Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::isDeleted, false)
                .eq(SysUserEntity::isEnabled, true);
        if ("USER".equals(targetType)) {
            if (targetUserId == null) {
                throw new BusinessException("NOTIFICATION_003", "请选择接收通知的销售");
            }
            wrapper.eq(SysUserEntity::getId, targetUserId);
        }
        return userMapper.selectList(wrapper);
    }

    private SysNotificationReceiptEntity findReceipt(Long tenantId, Long userId, Long receiptId) {
        if (receiptId == null) {
            throw new BusinessException("NOTIFICATION_005", "通知编号不能为空");
        }
        SysNotificationReceiptEntity receipt = receiptMapper.selectOne(
                Wrappers.<SysNotificationReceiptEntity>lambdaQuery()
                        .eq(SysNotificationReceiptEntity::getId, receiptId)
                        .eq(SysNotificationReceiptEntity::getTenantId, tenantId)
                        .eq(SysNotificationReceiptEntity::getUserId, userId));
        if (receipt == null) {
            throw new BusinessException("NOTIFICATION_006", "通知不存在");
        }
        return receipt;
    }

    private void validateRequest(NotificationSendRequest request) {
        if (request == null || trimToNull(request.getTitle()) == null) {
            throw new BusinessException("NOTIFICATION_001", "通知标题不能为空");
        }
        if (request.getTitle().trim().length() > 128) {
            throw new BusinessException("NOTIFICATION_001", "通知标题不能超过128个字符");
        }
        if (trimToNull(request.getContent()) == null) {
            throw new BusinessException("NOTIFICATION_002", "通知内容不能为空");
        }
        String targetType = trimToNull(request.getTargetType());
        if (!"ALL".equalsIgnoreCase(targetType) && !"USER".equalsIgnoreCase(targetType)) {
            throw new BusinessException("NOTIFICATION_003", "通知范围不正确");
        }
    }

    private String normalizeLevel(String level) {
        String value = trimToNull(level);
        if (value == null) {
            return "INFO";
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!"INFO".equals(value) && !"IMPORTANT".equals(value) && !"WARNING".equals(value)) {
            throw new BusinessException("NOTIFICATION_007", "通知级别不正确");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }
}
