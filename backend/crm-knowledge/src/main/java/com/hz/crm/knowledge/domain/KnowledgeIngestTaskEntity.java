package com.hz.crm.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.common.time.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "kb_ingest_task")
@TableName("kb_ingest_task")
public class KnowledgeIngestTaskEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long documentId;

    @Column(nullable = false)
    private Boolean force;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 64)
    private String stage;

    private Integer progress;

    @Column(length = 512)
    private String message;

    @Column(length = 1024)
    private String errorMessage;

    private Integer indexVersion;

    @Column(length = 64)
    private String indexHash;

    private Integer chunkCount;

    private Integer vectorDimension;

    @Column(length = 128)
    private String embeddingModel;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(nullable = false)
    private Boolean deleted;

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
