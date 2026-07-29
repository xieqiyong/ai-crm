package com.hz.crm.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.common.time.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "kb_change_outbox",
        indexes = {
                @Index(name = "idx_kb_change_outbox_publish", columnList = "published, created_at"),
                @Index(name = "idx_kb_change_outbox_tenant", columnList = "tenant_id, id")
        })
@TableName("kb_change_outbox")
public class KnowledgeChangeOutboxEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long documentId;

    @Column(columnDefinition = "bigint")
    private Long documentVersionId;

    @Column(columnDefinition = "bigint")
    private Long sourceIndexGenerationId;

    @Column(nullable = false, length = 32)
    private String eventType;

    @Column(nullable = false, length = 64)
    private String eventKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(nullable = false)
    private boolean published;

    private Integer publishAttempts;

    @Column(length = 512)
    private String errorMessage;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = DateTimes.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (publishAttempts == null) {
            publishAttempts = 0;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = DateTimes.now();
    }
}
