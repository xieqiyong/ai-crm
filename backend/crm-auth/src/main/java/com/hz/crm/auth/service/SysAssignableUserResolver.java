package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.common.user.AssignableUserResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SysAssignableUserResolver implements AssignableUserResolver {

    @Autowired
    private UserAssignmentScopeService assignmentScopeService;

    @Override
    public String resolveAssignableName(Long tenantId, Long operatorId, String dataScope, Long userId) {
        SysUserEntity user =
                assignmentScopeService.requireAssignableUser(tenantId, operatorId, dataScope, userId);
        return displayName(user);
    }

    private String displayName(SysUserEntity user) {
        if (user.getDisplayName() != null && user.getDisplayName().trim().length() > 0) {
            return user.getDisplayName().trim();
        }
        return user.getUsername();
    }
}
