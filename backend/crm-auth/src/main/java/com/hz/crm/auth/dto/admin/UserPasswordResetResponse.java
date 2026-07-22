package com.hz.crm.auth.dto.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPasswordResetResponse {

    private Long userId;

    private String temporaryPassword;
}
