package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.repository.SysUserRepository;
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

@Service
public class SysUserNameResolver implements UserNameResolver {

    @Autowired
    private SysUserRepository userRepository;

    @Override
    public Map<Long, String> resolve(Long tenantId, Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<Long, String>();
        if (tenantId == null || userIds == null || userIds.isEmpty()) {
            return result;
        }
        Set<Long> idSet = new HashSet<Long>();
        for (Long id : userIds) {
            if (id != null) {
                idSet.add(id);
            }
        }
        if (idSet.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<Long>(idSet);
        List<SysUserEntity> users = userRepository.findByTenantIdAndIdInAndDeletedFalse(tenantId, ids);
        for (SysUserEntity user : users) {
            result.put(user.getId(), displayName(user));
        }
        return result;
    }

    private String displayName(SysUserEntity user) {
        if (user.getDisplayName() != null && user.getDisplayName().trim().length() > 0) {
            return user.getDisplayName().trim();
        }
        return user.getUsername();
    }
}
