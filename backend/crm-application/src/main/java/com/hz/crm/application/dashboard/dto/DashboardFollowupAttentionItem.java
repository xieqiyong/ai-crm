package com.hz.crm.application.dashboard.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardFollowupAttentionItem {

    private String targetType;

    private Long targetId;

    private String targetName;

    private String companyName;

    private String contactName;

    private String contactPhone;

    private String productName;

    private Long ownerId;

    private String ownerName;

    private String followupHealth;

    private String followupHealthName;

    private String followupReason;

    private LocalDateTime lastFollowupAt;

    private LocalDateTime nextFollowTime;

    private LocalDateTime warningAt;
}
