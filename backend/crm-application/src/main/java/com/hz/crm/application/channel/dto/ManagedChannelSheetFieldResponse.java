package com.hz.crm.application.channel.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagedChannelSheetFieldResponse {

    private String fieldId;

    private String businessKey;

    private String title;

    private String fieldType;

    private boolean required;
}
