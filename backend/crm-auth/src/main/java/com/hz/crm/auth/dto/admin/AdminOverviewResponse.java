package com.hz.crm.auth.dto.admin;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminOverviewResponse {

    private List<DepartmentResponse> departments = new ArrayList<DepartmentResponse>();

    private List<UserResponse> users = new ArrayList<UserResponse>();

    private List<RoleResponse> roles = new ArrayList<RoleResponse>();

    private List<PermissionResponse> permissions = new ArrayList<PermissionResponse>();
}
