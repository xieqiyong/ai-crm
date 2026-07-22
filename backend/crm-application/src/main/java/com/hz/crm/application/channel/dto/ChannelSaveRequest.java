package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.ChannelType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelSaveRequest {

    private Long id;

    @NotBlank(message = "渠道标题不能为空")
    private String title;

    private ChannelType channelType = ChannelType.MANUAL;

    private String source;

    private String contactName;

    private String companyName;

    private String phone;

    private String email;

    private String remark;
}
