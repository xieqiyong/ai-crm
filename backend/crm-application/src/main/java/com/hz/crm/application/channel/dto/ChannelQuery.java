package com.hz.crm.application.channel.dto;

import com.hz.crm.common.api.PageQuery;
import com.hz.crm.domain.channel.ChannelStatus;
import com.hz.crm.domain.channel.ChannelType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelQuery extends PageQuery {

    private String keyword;

    private ChannelStatus status;

    private ChannelType channelType;
}
