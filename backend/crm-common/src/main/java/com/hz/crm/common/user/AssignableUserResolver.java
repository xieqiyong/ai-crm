package com.hz.crm.common.user;

public interface AssignableUserResolver {

    String resolveAssignableName(Long tenantId, Long operatorId, String dataScope, Long userId);
}
