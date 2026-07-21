package com.hz.crm.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    private String tenantId;

    private String username;

    private String password;
}
