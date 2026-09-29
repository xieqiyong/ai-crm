package com.hz.crm.application.channel.sheet;

import com.hz.crm.application.channel.dto.ChannelSourceResponse;
import java.util.List;

public interface ChannelSheetProvider {

    String platform();

    ChannelSheetDocument create(ChannelSheetCreateCommand command);

    List<ChannelSheetRecord> fetchRecords(Long tenantId, ChannelSourceResponse source);
}
