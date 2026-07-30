package com.hz.crm.application.mail.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MailSendRequest {

    private Long customerId;

    private String recipientEmail;

    private String subject;

    private String bodyHtml;
}
