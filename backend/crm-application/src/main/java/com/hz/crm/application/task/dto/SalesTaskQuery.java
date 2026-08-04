package com.hz.crm.application.task.dto;

import com.hz.crm.common.api.PageQuery;
import com.hz.crm.domain.task.SalesTaskPriority;
import com.hz.crm.domain.task.SalesTaskSource;
import com.hz.crm.domain.task.SalesTaskStatus;
import com.hz.crm.domain.task.SalesTaskTargetType;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesTaskQuery extends PageQuery {

    private String keyword;

    private SalesTaskStatus status;

    private SalesTaskPriority priority;

    private SalesTaskSource source;

    private SalesTaskTargetType targetType;

    private Long targetId;

    private Long ownerId;

    private LocalDateTime dueFrom;

    private LocalDateTime dueTo;
}
