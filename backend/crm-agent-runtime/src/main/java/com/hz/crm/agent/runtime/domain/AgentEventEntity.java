package com.hz.crm.agent.runtime.domain;

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
@Table(name = "agent_events")
@TableName("agent_events")
public class AgentEventEntity {

    @Id
    @TableId(type = IdType.INPUT)
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint")
    private Long tenantId;

    @Column(nullable = false)
    private Long runId;

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    private Long agentId;

    @Column(nullable = false)
    private Integer sequenceNo;

    @Column(length = 128)
    private String eventId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 128)
    private String toolName;

    @Column(columnDefinition = "text")
    private String metadataJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimes.now();
        }
    }
}
