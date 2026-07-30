package com.hz.crm.application.mail.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MailAttachment {

    private String name;

    private String contentType;

    private byte[] content;
}
