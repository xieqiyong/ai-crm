package com.hz.crm.auth.dto.notification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationSendRequest {

    private String title;

    private String content;

    private String level;

    private String targetType;

    private Long targetUserId;
}
