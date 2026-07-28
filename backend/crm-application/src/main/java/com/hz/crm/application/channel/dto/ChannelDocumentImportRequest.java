package com.hz.crm.application.channel.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelDocumentImportRequest {

    private String title;

    private String source;

    private String contactName;

    private String companyName;

    private String phone;

    private String email;

    private String mediaFileName;

    private String mediaContentType;

    private Long mediaSize;

    private String mediaStorageKey;

    private String documentText;

    private String remark;
}
