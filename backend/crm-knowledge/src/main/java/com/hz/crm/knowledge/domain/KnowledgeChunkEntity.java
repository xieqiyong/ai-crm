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
@Table(name = "kb_chunk")
@TableName("kb_chunk")
public class KnowledgeChunkEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long documentId;

    @Column(nullable = false)
    private Integer chunkIndex;

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

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 64)
    private String contentHash;

    @Column(length = 64)
    private String indexHash;

    private Integer indexVersion;

    private Integer tokenEstimate;

    @Column(length = 128)
    private String embeddingModel;

    private Integer vectorDimension;

    @Column(nullable = false, length = 32)
    private String vectorStatus;

    @Column(nullable = false)
    private boolean esIndexed;

    @Column(nullable = false)
    private boolean milvusIndexed;

    @Column(columnDefinition = "text")
    private String metadataJson;

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
