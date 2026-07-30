package com.hz.crm.application.mail.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MailAccountSaveRequest {

    private String host;

    private Integer port;

    private String username;

    private String password;

    private String fromAddress;

    private String fromName;

    private Boolean sslEnabled;

    private Boolean starttlsEnabled;

    private Boolean enabled;
}
