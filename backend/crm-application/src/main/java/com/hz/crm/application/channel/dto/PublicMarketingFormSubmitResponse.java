package com.hz.crm.application.channel.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublicMarketingFormSubmitResponse {

    private boolean submitted;

    private boolean leadCreated;

    private Long channelId;

    private Long leadId;

    private String message;
}
