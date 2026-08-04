package com.hz.crm.domain.task;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskCompletionRankingProjection {

    private String userId;

    private String userName;

    private Long completedTaskCount;

    private LocalDateTime lastCompletedAt;
}
