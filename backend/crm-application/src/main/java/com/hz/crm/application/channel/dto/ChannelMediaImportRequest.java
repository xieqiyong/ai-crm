package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.ChannelType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelMediaImportRequest {

    private String title;

    private ChannelType channelType;

    private String source;

    private String contactName;

    private String companyName;

    private String phone;

    private String email;

    private String mediaFileName;

    private String mediaContentType;

    private Long mediaSize;

    private String mediaStorageKey;
}
