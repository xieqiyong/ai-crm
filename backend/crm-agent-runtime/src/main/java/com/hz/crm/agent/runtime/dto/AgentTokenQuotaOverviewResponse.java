package com.hz.crm.agent.runtime.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentTokenQuotaOverviewResponse {

    private Long defaultDailyTokenLimit = 0L;

    private List<AgentTokenQuotaDepartmentOption> departments =
            new ArrayList<AgentTokenQuotaDepartmentOption>();

    private List<AgentTokenQuotaUserOption> users = new ArrayList<AgentTokenQuotaUserOption>();

    private List<AgentTokenQuotaUserResponse> quotas = new ArrayList<AgentTokenQuotaUserResponse>();
}
