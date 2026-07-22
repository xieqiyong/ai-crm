package com.hz.crm.auth.dto.admin;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleSaveRequest {

    private Long id;

    private String code;

    private String name;

    private String dataScope;

    private Boolean enabled;

    private List<String> permissionCodes = new ArrayList<String>();
}
