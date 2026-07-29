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
        name = "kb_index_generation",
        indexes = {
                @Index(name = "idx_kb_index_generation_status", columnList = "tenant_id, status")
        })
@TableName("kb_index_generation")
public class KnowledgeIndexGenerationEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 128)
    private String elasticsearchIndex;

    @Column(nullable = false, length = 128)
    private String milvusCollection;

    @Column(nullable = false, length = 128)
    private String embeddingModel;

    private Integer vectorDimension;

    @Column(nullable = false, length = 64)
    private String chunkProfileHash;

    private Long snapshotOutboxId;

    private Long replayedOutboxId;

    private Integer documentCount;

    private Integer completedDocumentCount;

    private Integer progress;

    @Column(length = 512)
    private String message;

    @Column(length = 1024)
    private String errorMessage;

    private LocalDateTime activatedAt;

    private LocalDateTime finishedAt;

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
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = DateTimes.now();
    }
}
