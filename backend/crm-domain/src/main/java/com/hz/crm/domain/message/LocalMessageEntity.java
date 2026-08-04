package com.hz.crm.domain.message;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "crm_local_message",
        indexes = {
                @Index(name = "idx_local_message_dispatch", columnList = "tenant_id,status,send_at"),
                @Index(name = "idx_local_message_business", columnList = "tenant_id,message_type,business_type,business_id")
        })
@TableName("crm_local_message")
public class LocalMessageEntity extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String messageType;

    @Column(nullable = false, length = 64)
    private String businessType;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long targetUserId;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 16)
    private String level = "INFO";

    @Column(nullable = false)
    private LocalDateTime sendAt;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    private LocalDateTime lockedAt;

    @Column(length = 64)
    private String lockedBy;

    private LocalDateTime sentAt;

    @Column(nullable = false)
    private Integer retryCount = Integer.valueOf(0);

    private LocalDateTime nextRetryAt;

    @Column(columnDefinition = "text")
    private String errorMessage;
}
