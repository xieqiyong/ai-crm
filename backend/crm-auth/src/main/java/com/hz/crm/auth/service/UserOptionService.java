package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.dto.UserOptionResponse;
import com.hz.crm.auth.repository.SysUserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserOptionService {

    @Autowired
    private SysUserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserOptionResponse> list(String tenantId, Long userId, String dataScope) {
        if ("SELF".equals(dataScope)) {
            return listSelf(tenantId, userId);
        }
        List<SysUserEntity> users = userRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId);
        List<UserOptionResponse> records = new ArrayList<UserOptionResponse>();
        for (SysUserEntity user : users) {
            if (!user.isEnabled()) {
                continue;
            }
            records.add(toResponse(user));
        }
        return records;
    }

    private List<UserOptionResponse> listSelf(String tenantId, Long userId) {
        List<UserOptionResponse> records = new ArrayList<UserOptionResponse>();
        Optional<SysUserEntity> user = userRepository.findByIdAndTenantIdAndDeletedFalse(userId, tenantId);
        if (user.isPresent() && user.get().isEnabled()) {
            records.add(toResponse(user.get()));
        }
        return records;
    }

    private UserOptionResponse toResponse(SysUserEntity user) {
        UserOptionResponse response = new UserOptionResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setName(displayName(user));
        return response;
    }

    private String displayName(SysUserEntity user) {
        if (user.getDisplayName() != null && user.getDisplayName().trim().length() > 0) {
            return user.getDisplayName().trim();
        }
        return user.getUsername();
    }
}
