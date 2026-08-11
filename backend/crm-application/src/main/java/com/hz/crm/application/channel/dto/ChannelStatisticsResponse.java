package com.hz.crm.application.channel.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelStatisticsResponse {

    private Long totalUserCount;

    private Long todayNewUserCount;

    private Long convertedLeadUserCount;
}
