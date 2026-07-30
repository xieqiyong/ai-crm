package com.hz.crm.common.user;

import java.util.List;

public interface UserDataScopeValidator {

    void checkOwnerAccess(Long tenantId, Long operatorId, String dataScope, Long ownerId);

    List<Long> listAccessibleUserIds(Long tenantId, Long operatorId, String dataScope);
}
