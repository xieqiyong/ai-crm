package com.hz.crm.auth.service;

import com.hz.crm.common.user.UserDataScopeValidator;
import com.hz.crm.auth.domain.SysUserEntity;
import java.util.ArrayList;
import java.util.List;
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

    @Override
    public List<Long> listAccessibleUserIds(Long tenantId, Long operatorId, String dataScope) {
        List<SysUserEntity> users =
                assignmentScopeService.listAssignableUsers(tenantId, operatorId, dataScope);
        List<Long> userIds = new ArrayList<Long>();
        for (SysUserEntity user : users) {
            userIds.add(user.getId());
        }
        return userIds;
    }
}
