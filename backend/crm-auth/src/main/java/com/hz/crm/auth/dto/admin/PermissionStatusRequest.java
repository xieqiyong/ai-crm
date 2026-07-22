package com.hz.crm.auth.dto.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionStatusRequest {

    private Long id;

    private Boolean enabled;
}
