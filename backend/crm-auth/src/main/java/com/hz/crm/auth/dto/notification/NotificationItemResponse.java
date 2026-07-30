package com.hz.crm.auth.dto.notification;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationItemResponse {

    private Long id;

    private String title;

    private String content;

    private String level;

    private String targetType;

    private String senderName;

    private LocalDateTime createdAt;

    private LocalDateTime readAt;
}
