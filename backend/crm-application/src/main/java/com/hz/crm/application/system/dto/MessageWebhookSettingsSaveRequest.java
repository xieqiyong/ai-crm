package com.hz.crm.application.system.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageWebhookSettingsSaveRequest {

    private boolean enabled;

    @Size(max = 32, message = "消息平台格式不正确")
    private String platform;

    @Size(max = 2000, message = "机器人Webhook地址不能超过2000个字符")
    private String webhookUrl;

    private boolean notifyPublicPool;
}
