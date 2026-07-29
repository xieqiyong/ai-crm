package com.hz.crm.common.user;

public interface UserDataScopeValidator {

    void checkOwnerAccess(Long tenantId, Long operatorId, String dataScope, Long ownerId);
}
