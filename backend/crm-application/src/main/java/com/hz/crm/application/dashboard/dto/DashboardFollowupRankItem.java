package com.hz.crm.application.dashboard.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardFollowupRankItem {

    private int rankNo;

    private String userId;

    private String userName;

    private long followupCount;

    private LocalDateTime lastFollowupAt;
}
