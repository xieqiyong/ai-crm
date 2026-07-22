package com.hz.crm.auth.dto.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserStatusRequest {

    private Long id;

    private Boolean enabled;
}
