package com.hz.crm.agent.runtime.domain;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "agent_token_usage",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_agent_token_usage_user_day",
                        columnNames = {"tenant_id", "user_id", "usage_date"})
        },
        indexes = {
                @Index(name = "idx_agent_token_usage_tenant_day", columnList = "tenant_id, usage_date"),
                @Index(name = "idx_agent_token_usage_user_day", columnList = "tenant_id, user_id, usage_date")
        })
@TableName("agent_token_usage")
public class AgentTokenUsageEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false)
    private Long inputTokenCount = 0L;

    @Column(nullable = false)
    private Long outputTokenCount = 0L;

    @Column(nullable = false)
    private Long totalTokenCount = 0L;

    @Column(nullable = false)
    private Long estimatedTokenCount = 0L;

    @Column(nullable = false)
    private Long reservedTokenCount = 0L;

    @Column(nullable = false)
    private Long requestCount = 0L;

    @Column(nullable = false)
    private Long successCount = 0L;

    @Column(nullable = false)
    private Long failedCount = 0L;

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
