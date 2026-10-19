package com.hz.crm.application.channel.sheet;

import com.hz.crm.application.channel.dto.ChannelSourceResponse;
import java.util.List;

public interface ChannelSheetProvider {

    String platform();

    ChannelSheetDocument create(ChannelSheetCreateCommand command);

    List<ChannelSheetRecord> fetchRecords(Long tenantId, ChannelSourceResponse source);

    List<ChannelSheetFieldDefinition> fetchFields(Long tenantId, ChannelSourceResponse source);

    void addFields(
            Long tenantId,
            ChannelSourceResponse source,
            List<ChannelSheetFieldDefinition> fields);

    void updateFields(
            Long tenantId,
            ChannelSourceResponse source,
            List<ChannelSheetFieldDefinition> fields);

    void deleteFields(
            Long tenantId,
            ChannelSourceResponse source,
            List<String> fieldIds);

    void deleteDocument(Long tenantId, ChannelSourceResponse source);
}
