package com.hz.crm.mcp.support;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrmMcpContext {

    private Long tenantId;

    private Long userId;

    private String dataScope;
}
