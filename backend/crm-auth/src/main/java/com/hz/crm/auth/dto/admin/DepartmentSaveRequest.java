package com.hz.crm.auth.dto.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentSaveRequest {

    private Long id;

    private Long parentId;

    private String code;

    private String name;

    private Integer sortNo;

    private Boolean enabled;
}
