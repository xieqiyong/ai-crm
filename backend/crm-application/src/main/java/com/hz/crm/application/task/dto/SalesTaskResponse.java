package com.hz.crm.application.task.dto;

import com.hz.crm.domain.task.SalesTaskPriority;
import com.hz.crm.domain.task.SalesTaskSource;
import com.hz.crm.domain.task.SalesTaskStatus;
import com.hz.crm.domain.task.SalesTaskTargetType;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesTaskResponse {

    private Long id;

    private Long tenantId;

    private String title;

    private String content;

    private SalesTaskTargetType targetType;

    private Long targetId;

    private String targetName;

    private Long ownerId;

    private String ownerName;

    private Long creatorId;

    private String creatorName;

    private LocalDateTime dueAt;

    private LocalDateTime reminderAt;

    private SalesTaskPriority priority;

    private SalesTaskStatus status;

    private SalesTaskSource source;

    private Long sourceId;

    private LocalDateTime completedAt;

    private Long completedBy;

    private String completedByName;

    private String cancelReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
