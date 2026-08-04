package com.hz.crm.domain.followup;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupRankingProjection {

    private String userId;

    private String userName;

    private Long followupCount;

    private LocalDateTime lastFollowupAt;
}
