package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.MarketingFormFieldType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketingFormFieldRequest {

    private Long id;

    private String fieldKey;

    private String label;

    private MarketingFormFieldType fieldType = MarketingFormFieldType.TEXT;

    private boolean requiredField;

    private String placeholder;

    private String optionsText;

    private String systemMapping;

    private Integer sortOrder;
}
