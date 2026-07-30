package com.hz.crm.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.common.time.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "sys_notification_receipt",
        indexes = @Index(
                name = "idx_notification_receipt_user_read",
                columnList = "tenant_id,user_id,read_at,created_at"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_receipt_user",
                columnNames = {"tenant_id", "notification_id", "user_id"}))
@TableName("sys_notification_receipt")
public class SysNotificationReceiptEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false)
    private Long notificationId;

    @Column(nullable = false)
    private Long userId;

    private LocalDateTime readAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimes.now();
        }
    }
}
