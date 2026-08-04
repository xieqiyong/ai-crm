package com.hz.crm.mcp.user;

import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.user.UserDataScopeValidator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class McpUserDataScopeValidator implements UserDataScopeValidator {

    @Autowired
    private McpUserNameResolver userNameResolver;

    @Override
    public void checkOwnerAccess(Long tenantId, Long operatorId, String dataScope, Long ownerId) {
        if (isSelfScope(dataScope) && (ownerId == null || !ownerId.equals(operatorId))) {
            throw new BusinessException("MCP_DATA_001", "无权访问该数据");
        }
    }

    @Override
    public List<Long> listAccessibleUserIds(Long tenantId, Long operatorId, String dataScope) {
        List<Long> userIds = new ArrayList<Long>();
        if (isSelfScope(dataScope)) {
            if (operatorId != null) {
                userIds.add(operatorId);
            }
            return userIds;
        }
        List<McpSysUserEntity> users = userNameResolver.listEnabledUsers(tenantId);
        for (McpSysUserEntity user : users) {
            if (user.getId() != null) {
                userIds.add(user.getId());
            }
        }
        return userIds;
    }

    private boolean isSelfScope(String dataScope) {
        return dataScope == null || !"ALL".equalsIgnoreCase(dataScope);
    }
}
