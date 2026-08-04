package com.hz.crm.application.dashboard.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardTaskRankItem {

    private int rankNo;

    private String userId;

    private String userName;

    private long completedTaskCount;

    private LocalDateTime lastCompletedAt;
}
