package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.MarketingFormStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketingFormSaveRequest {

    private Long id;

    private String title;

    private String description;

    private String source;

    private String submitMessage;

    private MarketingFormStatus status = MarketingFormStatus.PUBLISHED;

    private boolean autoCreateLead = true;

    private Long ownerId;

    private List<MarketingFormFieldRequest> fields = new ArrayList<MarketingFormFieldRequest>();
}
