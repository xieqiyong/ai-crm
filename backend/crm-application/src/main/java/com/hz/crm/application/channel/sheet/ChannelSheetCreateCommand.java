package com.hz.crm.application.channel.sheet;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSheetCreateCommand {

    private Long tenantId;

    private Long operatorId;

    private Long configId;

    private String name;

    private List<ChannelSheetFieldDefinition> fields = new ArrayList<ChannelSheetFieldDefinition>();
}
