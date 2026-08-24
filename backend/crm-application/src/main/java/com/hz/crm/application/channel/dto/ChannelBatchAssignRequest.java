package com.hz.crm.application.channel.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelBatchAssignRequest {

    private List<Long> ids;

    private Long ownerId;
}
