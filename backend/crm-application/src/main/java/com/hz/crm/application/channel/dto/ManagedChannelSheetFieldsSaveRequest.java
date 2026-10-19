package com.hz.crm.application.channel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagedChannelSheetFieldsSaveRequest {

    @NotNull(message = "自建表格编号不能为空")
    private Long id;

    @Valid
    private List<ManagedChannelSheetFieldRequest> fields = new ArrayList<ManagedChannelSheetFieldRequest>();
}
