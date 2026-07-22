package com.hz.crm.auth.dto.admin;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSaveRequest {

    private Long id;

    private String username;

    private String displayName;

    private Long departmentId;

    private String password;

    private Boolean enabled;

    private List<Long> roleIds = new ArrayList<Long>();
}
