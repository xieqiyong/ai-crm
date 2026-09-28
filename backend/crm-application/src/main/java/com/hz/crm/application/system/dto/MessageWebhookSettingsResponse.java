package com.hz.crm.application.system.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageWebhookSettingsResponse {

    private boolean enabled;

    private String platform;

    private String webhookUrl;

    private boolean notifyPublicPool;
}
