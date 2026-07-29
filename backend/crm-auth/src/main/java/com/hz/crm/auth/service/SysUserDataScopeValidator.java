package com.hz.crm.auth.service;

import com.hz.crm.common.user.UserDataScopeValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SysUserDataScopeValidator implements UserDataScopeValidator {

    @Autowired
    private UserAssignmentScopeService assignmentScopeService;

    @Override
    public void checkOwnerAccess(Long tenantId, Long operatorId, String dataScope, Long ownerId) {
        assignmentScopeService.checkOwnerAccess(tenantId, operatorId, dataScope, ownerId);
    }
}
