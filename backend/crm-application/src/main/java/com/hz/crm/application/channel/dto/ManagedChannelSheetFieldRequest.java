package com.hz.crm.application.channel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagedChannelSheetFieldRequest {

    private String businessKey;

    @NotBlank(message = "字段名称不能为空")
    private String title;

    @NotBlank(message = "字段类型不能为空")
    private String fieldType;
}
