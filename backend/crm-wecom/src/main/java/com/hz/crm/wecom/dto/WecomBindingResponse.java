package com.hz.crm.wecom.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WecomBindingResponse {

    private Long id;

    private String wecomUserId;

    private String wecomUserName;

    private Long crmUserId;

    private boolean enabled;
}
