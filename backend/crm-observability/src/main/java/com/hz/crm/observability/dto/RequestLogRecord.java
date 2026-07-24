package com.hz.crm.observability.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestLogRecord {

    private Long tenantId;

    private Long operatorId;

    private String username;

    private String traceId;

    private String requestMethod;

    private String requestUri;

    private String clientIp;

    private String userAgent;

    private Integer statusCode;

    private Long costMillis;

    private boolean success;

    private String errorCode;

    private String errorMessage;
}
