package com.hz.crm.wecom.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WecomConfigResponse {

    private Long id;

    private String name;

    private String corpId;

    private boolean secretConfigured;

    private boolean enabled;

    private Integer syncIntervalMinutes;

    private Long defaultOwnerId;

    private String lastSyncStatus;

    private LocalDateTime lastSyncAt;

    private LocalDateTime lastSuccessAt;

    private String lastError;
}
