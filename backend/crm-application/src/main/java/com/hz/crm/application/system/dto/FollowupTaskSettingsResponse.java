package com.hz.crm.application.system.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupTaskSettingsResponse {

    private int firstDelayMinutes;

    private int secondDelayMinutes;

    private int defaultFirstDelayMinutes;

    private int defaultSecondDelayMinutes;
}
