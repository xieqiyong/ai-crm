package com.hz.crm.application.followup.dto;

import com.hz.crm.common.api.PageQuery;
import com.hz.crm.domain.followup.FollowupTargetType;
import com.hz.crm.domain.followup.FollowupType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupQuery extends PageQuery {

    private String keyword;

    private FollowupTargetType targetType;

    private Long targetId;

    private FollowupType followupType;
}
