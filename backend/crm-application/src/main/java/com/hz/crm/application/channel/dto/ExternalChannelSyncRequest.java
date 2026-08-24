package com.hz.crm.application.channel.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExternalChannelSyncRequest {

    private String externalProvider;

    private String externalKey;

    private String externalVersion;

    private String title;

    private String source;

    private String contactName;

    private String companyName;

    private String phone;

    private String email;

    private Long ownerId;

    private Long productId;

    private String remark;

    private LocalDateTime occurredAt;

    private String sourceSnapshot;
}
