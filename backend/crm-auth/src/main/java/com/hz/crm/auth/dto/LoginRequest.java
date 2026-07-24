package com.hz.crm.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    private Long tenantId;

    private String username;

    private String password;
}
