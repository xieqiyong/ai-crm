package com.hz.crm.application.channel.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagedChannelSheetDeleteRequest {

    @NotNull(message = "自建表格编号不能为空")
    private Long id;

    private boolean deletePlatformDocument;
}
