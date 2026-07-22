package com.hz.crm.auth.dto.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionSaveRequest {

    private Long id;

    private String code;

    private String name;

    private String permissionType;

    private Long parentId;

    private String routePath;

    private Integer sortNo;

    private Boolean enabled;
}
