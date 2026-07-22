package com.hz.crm.auth.dto.admin;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleResponse {

    private Long id;

    private String code;

    private String name;

    private String dataScope;

    private boolean enabled;

    private long userCount;

    private List<String> permissionCodes = new ArrayList<String>();

    private List<String> menuPermissionCodes = new ArrayList<String>();

    private List<String> dataPermissionCodes = new ArrayList<String>();
}
