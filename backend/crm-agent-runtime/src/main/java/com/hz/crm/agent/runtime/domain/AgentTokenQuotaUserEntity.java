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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "agent_token_quota_user",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_agent_token_quota_user",
                        columnNames = {"tenant_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_agent_token_quota_user_tenant", columnList = "tenant_id"),
                @Index(name = "idx_agent_token_quota_user_user", columnList = "tenant_id, user_id")
        })
@TableName("agent_token_quota_user")
public class AgentTokenQuotaUserEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long dailyTokenLimit = 0L;

    @Column(nullable = false, length = 32)
    private String assignScope = "USER";

    private Long assignTargetId;

    @Column(length = 128)
    private String assignTargetName;

    @Column(length = 512)
    private String remark;

    @Column(nullable = false)
    private boolean enabled = true;

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
