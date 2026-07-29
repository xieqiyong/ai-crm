package com.hz.crm.observability.dto;

import com.hz.crm.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditLogQuery extends PageQuery {

    private String module;

    private String action;

    private String targetType;

    private Long operatorId;
}
