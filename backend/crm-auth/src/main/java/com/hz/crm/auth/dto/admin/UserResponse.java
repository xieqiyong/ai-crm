package com.hz.crm.auth.dto.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponse {

    private Long id;

    private String username;

    private String displayName;

    private Long departmentId;

    private String departmentName;

    private String dataScope;

    private boolean enabled;

    private LocalDateTime createdAt;

    private List<Long> roleIds = new ArrayList<Long>();

    private List<String> roleNames = new ArrayList<String>();
}
