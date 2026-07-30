package com.hz.crm.application.mail.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MailAccountResponse {

    private boolean configured;

    private String host;

    private Integer port;

    private String username;

    private boolean passwordConfigured;

    private String fromAddress;

    private String fromName;

    private boolean sslEnabled;

    private boolean starttlsEnabled;

    private boolean enabled;
}
