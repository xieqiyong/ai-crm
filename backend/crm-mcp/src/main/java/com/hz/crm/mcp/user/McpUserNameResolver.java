package com.hz.crm.mcp.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.common.user.UserNameResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class McpUserNameResolver implements UserNameResolver {

    @Autowired
    private McpSysUserMapper userMapper;

    @Override
    public Map<Long, String> resolve(Long tenantId, Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<Long, String>();
        if (tenantId == null || userIds == null || userIds.isEmpty()) {
            return result;
        }
        Set<Long> idSet = new HashSet<Long>();
        for (Long userId : userIds) {
            if (userId != null) {
                idSet.add(userId);
            }
        }
        if (idSet.isEmpty()) {
            return result;
        }
        QueryWrapper<McpSysUserEntity> wrapper = baseWrapper(tenantId);
        wrapper.in("id", new ArrayList<Long>(idSet));
        List<McpSysUserEntity> users = userMapper.selectList(wrapper);
        for (McpSysUserEntity user : users) {
            result.put(user.getId(), displayName(user));
        }
        return result;
    }

    public McpSysUserEntity findEnabledUser(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return null;
        }
        QueryWrapper<McpSysUserEntity> wrapper = baseWrapper(tenantId);
        wrapper.eq("id", userId);
        return userMapper.selectOne(wrapper);
    }

    public List<McpSysUserEntity> listEnabledUsers(Long tenantId) {
        if (tenantId == null) {
            return new ArrayList<McpSysUserEntity>();
        }
        return userMapper.selectList(baseWrapper(tenantId));
    }

    private QueryWrapper<McpSysUserEntity> baseWrapper(Long tenantId) {
        QueryWrapper<McpSysUserEntity> wrapper = new QueryWrapper<McpSysUserEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.eq("enabled", true);
        return wrapper;
    }

    public String displayName(McpSysUserEntity user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getDisplayName())) {
            return user.getDisplayName().trim();
        }
        return user.getUsername();
    }
}
