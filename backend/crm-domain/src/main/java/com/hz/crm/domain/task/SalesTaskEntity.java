package com.hz.crm.domain.task;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hz.crm.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "crm_sales_task")
@TableName("crm_sales_task")
public class SalesTaskEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SalesTaskTargetType targetType = SalesTaskTargetType.GENERAL;

    private Long targetId;

    @Column(length = 128)
    private String targetName;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long creatorId;

    @Column(nullable = false)
    private LocalDateTime dueAt;

    private LocalDateTime reminderAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SalesTaskPriority priority = SalesTaskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SalesTaskStatus status = SalesTaskStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SalesTaskSource source = SalesTaskSource.MANUAL;

    private Long sourceId;

    private LocalDateTime completedAt;

    private Long completedBy;

    @Column(length = 256)
    private String cancelReason;
}
