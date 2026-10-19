package com.hz.crm.application.channel.sheet;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSheetFieldDefinition {

    private String fieldId;

    private String businessKey;

    private String title;

    private String fieldType;

    private boolean required;
}
