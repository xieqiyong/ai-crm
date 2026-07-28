package com.hz.crm.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.common.time.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "kb_ingest_event")
@TableName("kb_ingest_event")
public class KnowledgeIngestEventEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long taskId;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long documentId;

    @Column(length = 64)
    private String stage;

    @Column(length = 32)
    private String status;

    @Column(length = 512)
    private String message;

    @Column(columnDefinition = "text")
    private String detailJson;

    private Long elapsedMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimes.now();
        }
    }
}
