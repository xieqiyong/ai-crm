package com.hz.crm.agent.runtime.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentTokenQuotaUserOption {

    private Long id;

    private String username;

    private String displayName;

    private Long departmentId;

    private String departmentName;

    private boolean enabled;
}
