package com.hz.crm.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetResponse {

    private boolean accepted;

    private boolean resetTokenExposed;

    private String resetToken;

    private Long ttlSeconds;

    private String message;
}
