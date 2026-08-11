package com.hz.crm.application.followup.dto;

import com.hz.crm.domain.followup.FollowupTargetType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupTargetOptionQuery {

    private FollowupTargetType targetType;

    private String keyword;

    private Integer limit;
}
