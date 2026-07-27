package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.MarketingFormFieldType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketingFormFieldResponse {

    private Long id;

    private String fieldKey;

    private String label;

    private MarketingFormFieldType fieldType;

    private boolean requiredField;

    private String placeholder;

    private String optionsText;

    private String systemMapping;

    private Integer sortOrder;
}
