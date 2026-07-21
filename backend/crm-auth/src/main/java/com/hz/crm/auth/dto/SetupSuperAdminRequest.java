package com.hz.crm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetupSuperAdminRequest {

    private String tenantId;

    @NotBlank(message = "超管用户名不能为空")
    private String username;

    @NotBlank(message = "超管密码不能为空")
    private String password;

    private String displayName;
}
