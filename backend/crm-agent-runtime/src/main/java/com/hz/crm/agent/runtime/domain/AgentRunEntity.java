package com.hz.crm.agent.runtime.domain;

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
@Table(name = "agent_run")
@TableName("agent_run")
public class AgentRunEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long agentId;

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false, length = 128)
    private String sessionId;

    @Column(length = 64)
    private String sceneCode;

    @Column(length = 64)
    private String businessType;

    @Column(length = 64)
    private String businessId;

    @Column(nullable = false, length = 32)
    private String status = "RUNNING";

    @Column(columnDefinition = "text")
    private String inputText;

    @Column(columnDefinition = "text")
    private String inputJson;

    @Column(columnDefinition = "text")
    private String outputText;

    @Column(length = 1000)
    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long elapsedMs;

    private Long inputTokenCount;

    private Long outputTokenCount;

    private Long totalTokenCount;

    private Long estimatedTokenCount;

    private boolean usageEstimated = true;

    private Long reservedTokenCount;

    private Long dailyTokenLimit;

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
        if (startedAt == null) {
            startedAt = now;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = DateTimes.now();
    }
}
