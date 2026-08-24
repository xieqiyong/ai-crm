package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.ChannelSourceKind;
import com.hz.crm.domain.channel.ChannelSourceStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSourceQuery {

    private String keyword;

    private ChannelSourceKind sourceType;

    private ChannelSourceStatus status;
}
