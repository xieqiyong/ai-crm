package com.hz.crm.application.task.dto;

import com.hz.crm.domain.task.SalesTaskPriority;
import com.hz.crm.domain.task.SalesTaskTargetType;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesTaskSaveRequest {

    private Long id;

    private String title;

    private String content;

    private SalesTaskTargetType targetType;

    private Long targetId;

    private String targetName;

    private Long ownerId;

    private LocalDateTime dueAt;

    private LocalDateTime reminderAt;

    private SalesTaskPriority priority;
}
