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
        name = "kb_document",
        indexes = {
                @Index(name = "idx_kb_document_source", columnList = "tenant_id, source_key"),
                @Index(
                        name = "idx_kb_document_active_version",
                        columnList = "tenant_id, active_version_id, deleted")
        })
@TableName("kb_document")
public class KnowledgeDocumentEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 128)
    private String sourceType;

    @Column(length = 128)
    private String category;

    @Column(length = 512)
    private String tags;

    @Column(length = 512)
    private String sourceUrl;

    @Column(length = 512)
    private String objectKey;

    @Column(nullable = false, length = 512)
    private String sourceKey;

    @Column(length = 64)
    private String rawFileHash;

    @Column(nullable = false, length = 64)
    private String normalizedContentHash;

    @Column(columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 32)
    private String vectorStatus;

    private Integer chunkCount;

    private Integer vectorDimension;

    @Column(length = 128)
    private String embeddingModel;

    @Column(length = 64)
    private String indexHash;

    private Integer indexVersion;

    private Long activeVersionId;

    private Long pendingVersionId;

    private LocalDateTime indexedAt;

    @Column(length = 512)
    private String errorMessage;

    @Column(nullable = false)
    private boolean deleted;

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
