package com.hz.crm.application.followup.dto;

import com.hz.crm.domain.followup.FollowupTargetType;
import com.hz.crm.domain.followup.FollowupType;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupResponse {

    private Long id;

    private Long tenantId;

    private FollowupTargetType targetType;

    private Long targetId;

    private String targetName;

    private FollowupType followupType;

    private LocalDateTime followupAt;

    private String content;

    private String result;

    private String nextPlan;

    private LocalDateTime nextFollowTime;

    private Long ownerId;

    private String ownerName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
