package com.hz.crm.auth.security;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JwtPrincipal {

    private Long userId;

    private String tenantId;

    private String username;

    private String displayName;

    private List<String> permissions = new ArrayList<String>();

    private List<String> menuPermissions = new ArrayList<String>();

    private List<String> dataPermissions = new ArrayList<String>();

    private String dataScope;
}
