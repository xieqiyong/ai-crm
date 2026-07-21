package com.hz.crm.auth.dto;

import com.hz.crm.auth.domain.DataScope;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionProfile {

    private Set<String> permissions = new HashSet<String>();

    private Set<String> menuPermissions = new HashSet<String>();

    private Set<String> dataPermissions = new HashSet<String>();

    private DataScope dataScope = DataScope.SELF;
}
