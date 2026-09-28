package com.hz.crm.application.dashboard.dto;

import com.hz.crm.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardFollowupAttentionQuery extends PageQuery {

    private String followupHealth;

    private String targetType;

    private String keyword;
}
