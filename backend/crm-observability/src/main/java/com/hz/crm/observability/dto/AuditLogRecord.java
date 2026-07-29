package com.hz.crm.observability.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditLogRecord {

    private Long tenantId;

    private Long operatorId;

    private String action;

    private String targetType;

    private Long targetId;

    private String detailJson;
}
