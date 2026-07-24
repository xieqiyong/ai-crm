package com.hz.crm.agent.runtime.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentTokenQuotaDepartmentOption {

    private Long id;

    private Long parentId;

    private String name;

    private boolean enabled;
}
