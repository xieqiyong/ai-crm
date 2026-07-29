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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "kb_document_version",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_kb_document_version_no",
                        columnNames = {"tenant_id", "document_id", "version_no"})
        },
        indexes = {
                @Index(name = "idx_kb_document_version_document", columnList = "tenant_id, document_id"),
                @Index(name = "idx_kb_document_version_status", columnList = "tenant_id, status")
        })
@TableName("kb_document_version")
public class KnowledgeDocumentVersionEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long documentId;

    @Column(nullable = false)
    private Integer versionNo;

    @Column(nullable = false, length = 512)
    private String sourceKey;

    @Column(length = 64)
    private String rawFileHash;

    @Column(nullable = false, length = 64)
    private String normalizedContentHash;

    @Column(nullable = false, length = 64)
    private String buildFingerprint;

    @Column(nullable = false, length = 32)
    private String status;

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

    @Column(nullable = false, columnDefinition = "text")
    private String contentSnapshot;

    private Integer chunkCount;

    private Integer vectorDimension;

    @Column(length = 128)
    private String embeddingModel;

    @Column(length = 512)
    private String errorMessage;

    private LocalDateTime readyAt;

    private LocalDateTime activatedAt;

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
