package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.dto.UserOptionResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserOptionService {

    @Autowired
    private UserAssignmentScopeService assignmentScopeService;

    public List<UserOptionResponse> list(Long tenantId, Long userId, String dataScope) {
        List<SysUserEntity> users = assignmentScopeService.listAssignableUsers(tenantId, userId, dataScope);
        List<UserOptionResponse> records = new ArrayList<UserOptionResponse>();
        for (SysUserEntity user : users) {
            records.add(toResponse(user));
        }
        return records;
    }

    private UserOptionResponse toResponse(SysUserEntity user) {
        UserOptionResponse response = new UserOptionResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setName(displayName(user));
        return response;
    }

    private String displayName(SysUserEntity user) {
        if (user.getDisplayName() != null && user.getDisplayName().trim().length() > 0) {
            return user.getDisplayName().trim();
        }
        return user.getUsername();
    }
}
