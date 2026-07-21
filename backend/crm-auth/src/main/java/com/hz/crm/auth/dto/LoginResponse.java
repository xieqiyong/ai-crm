package com.hz.crm.auth.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponse {

    private String token;

    private Long userId;

    private String tenantId;

    private String username;

    private String displayName;

    private List<String> permissions = new ArrayList<String>();

    private List<String> menuPermissions = new ArrayList<String>();

    private List<String> dataPermissions = new ArrayList<String>();

    private String dataScope;
}
