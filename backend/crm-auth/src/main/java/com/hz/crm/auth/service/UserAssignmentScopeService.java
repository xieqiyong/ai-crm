package com.hz.crm.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.auth.domain.SysDepartmentEntity;
import com.hz.crm.auth.domain.SysUserEntity;
import com.hz.crm.auth.mapper.SysDepartmentMapper;
import com.hz.crm.auth.mapper.SysUserMapper;
import com.hz.crm.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserAssignmentScopeService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysDepartmentMapper departmentMapper;

    public List<SysUserEntity> listAssignableUsers(Long tenantId, Long operatorId, String dataScope) {
        LambdaQueryWrapper<SysUserEntity> query = Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::isDeleted, false)
                .eq(SysUserEntity::isEnabled, true);
        if ("ALL".equals(dataScope)) {
            // 全部数据权限不追加负责人范围条件
        } else if ("SELF".equals(dataScope)) {
            query.eq(SysUserEntity::getId, operatorId);
        } else if ("DEPARTMENT".equals(dataScope) || "DEPARTMENT_AND_CHILD".equals(dataScope)) {
            List<Long> departmentIds = resolveDepartmentIds(tenantId, operatorId, dataScope);
            if (departmentIds.isEmpty()) {
                return new ArrayList<SysUserEntity>();
            }
            query.in(SysUserEntity::getDepartmentId, departmentIds);
        } else {
            query.eq(SysUserEntity::getId, operatorId);
        }
        query.orderByAsc(SysUserEntity::getDisplayName).orderByAsc(SysUserEntity::getUsername);
        return userMapper.selectList(query);
    }

    public SysUserEntity requireAssignableUser(
            Long tenantId, Long operatorId, String dataScope, Long targetUserId) {
        SysUserEntity user = userMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getId, targetUserId)
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::isEnabled, true)
                .eq(SysUserEntity::isDeleted, false));
        if (user == null || !withinScope(tenantId, operatorId, dataScope, user)) {
            throw new BusinessException("USER_ASSIGN_002", "负责人不存在、已停用或超出当前数据权限范围");
        }
        return user;
    }

    public void checkOwnerAccess(Long tenantId, Long operatorId, String dataScope, Long ownerId) {
        if ("ALL".equals(dataScope)) {
            return;
        }
        if (ownerId == null) {
            throw new BusinessException("DATA_001", "无权访问该数据");
        }
        SysUserEntity owner = userMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getId, ownerId)
                .eq(SysUserEntity::getTenantId, tenantId));
        if (owner == null || !withinScope(tenantId, operatorId, dataScope, owner)) {
            throw new BusinessException("DATA_001", "无权访问该数据");
        }
    }

    private boolean withinScope(
            Long tenantId, Long operatorId, String dataScope, SysUserEntity targetUser) {
        if ("SELF".equals(dataScope)) {
            return targetUser.getId().equals(operatorId);
        }
        if ("DEPARTMENT".equals(dataScope) || "DEPARTMENT_AND_CHILD".equals(dataScope)) {
            List<Long> departmentIds = resolveDepartmentIds(tenantId, operatorId, dataScope);
            return targetUser.getDepartmentId() != null && departmentIds.contains(targetUser.getDepartmentId());
        }
        return "ALL".equals(dataScope) || targetUser.getId().equals(operatorId);
    }

    private List<Long> resolveDepartmentIds(Long tenantId, Long operatorId, String dataScope) {
        SysUserEntity operator = userMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getId, operatorId)
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::isDeleted, false));
        List<Long> result = new ArrayList<Long>();
        if (operator == null || operator.getDepartmentId() == null) {
            return result;
        }
        result.add(operator.getDepartmentId());
        if (!"DEPARTMENT_AND_CHILD".equals(dataScope)) {
            return result;
        }
        List<SysDepartmentEntity> departments = departmentMapper.selectList(
                Wrappers.<SysDepartmentEntity>lambdaQuery()
                        .eq(SysDepartmentEntity::getTenantId, tenantId)
                        .eq(SysDepartmentEntity::isDeleted, false)
                        .eq(SysDepartmentEntity::isEnabled, true));
        Set<Long> included = new HashSet<Long>();
        included.add(operator.getDepartmentId());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SysDepartmentEntity department : departments) {
                if (department.getParentId() != null
                        && included.contains(department.getParentId())
                        && included.add(department.getId())) {
                    changed = true;
                }
            }
        }
        return new ArrayList<Long>(included);
    }
}
