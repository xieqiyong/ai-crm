package com.hz.crm.application.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagedChannelSheetCreateRequest {

    @NotBlank(message = "表格名称不能为空")
    private String name;

    @NotBlank(message = "表格平台不能为空")
    private String platform;

    @NotNull(message = "关联产品不能为空")
    private Long productId;

    private Long configId;

    @Valid
    private List<ManagedChannelSheetFieldRequest> fields = new ArrayList<ManagedChannelSheetFieldRequest>();
}
