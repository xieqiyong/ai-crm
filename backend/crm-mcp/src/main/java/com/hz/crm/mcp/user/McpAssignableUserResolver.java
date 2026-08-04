package com.hz.crm.mcp.user;

import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.user.AssignableUserResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class McpAssignableUserResolver implements AssignableUserResolver {

    @Autowired
    private McpUserNameResolver userNameResolver;

    @Override
    public String resolveAssignableName(Long tenantId, Long operatorId, String dataScope, Long userId) {
        if (userId == null) {
            throw new BusinessException("MCP_USER_001", "用户编号不能为空");
        }
        if (isSelfScope(dataScope) && !userId.equals(operatorId)) {
            throw new BusinessException("MCP_USER_002", "本人数据权限不能访问其他用户");
        }
        McpSysUserEntity user = userNameResolver.findEnabledUser(tenantId, userId);
        if (user == null) {
            throw new BusinessException("MCP_USER_003", "用户不存在或已停用");
        }
        return userNameResolver.displayName(user);
    }

    private boolean isSelfScope(String dataScope) {
        return dataScope == null || !"ALL".equalsIgnoreCase(dataScope);
    }
}
