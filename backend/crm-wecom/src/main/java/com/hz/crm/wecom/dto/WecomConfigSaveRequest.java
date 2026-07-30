package com.hz.crm.wecom.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WecomConfigSaveRequest {

    private Long id;

    private String name;

    private String corpId;

    private String corpSecret;

    private boolean enabled = true;

    private Integer syncIntervalMinutes = 10;

    private Long defaultOwnerId;
}
