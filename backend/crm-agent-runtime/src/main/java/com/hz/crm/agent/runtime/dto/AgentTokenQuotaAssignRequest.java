package com.hz.crm.agent.runtime.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentTokenQuotaAssignRequest {

    private String scope;

    private Long departmentId;

    private List<Long> userIds = new ArrayList<Long>();

    private Long dailyTokenLimit;

    private Boolean enabled;

    private String remark;
}
