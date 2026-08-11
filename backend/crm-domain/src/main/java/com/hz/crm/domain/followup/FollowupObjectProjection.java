package com.hz.crm.domain.followup;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupObjectProjection {

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long followupCount;
}
