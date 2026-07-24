package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.SysDepartmentEntity;
import com.hz.crm.auth.repository.SysDepartmentRepository;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentSeedService {

    private static final String ROOT_DEPARTMENT_CODE = "ROOT";

    @Autowired
    private SysDepartmentRepository departmentRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public SysDepartmentEntity ensureTenantRootDepartment(Long tenantId) {
        return ensureTenantRootDepartment(tenantId, null);
    }

    @Transactional
    public SysDepartmentEntity ensureTenantRootDepartment(Long tenantId, String rootName) {
        SysDepartmentEntity rootDepartment = departmentRepository
                .findFirstByTenantIdAndParentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(tenantId)
                .orElse(null);
        if (rootDepartment != null) {
            return rootDepartment;
        }
        return departmentRepository
                .findByCodeAndTenantIdAndDeletedFalse(ROOT_DEPARTMENT_CODE, tenantId)
                .orElseGet(() -> createRootDepartment(tenantId, rootName));
    }

    private SysDepartmentEntity createRootDepartment(Long tenantId, String rootName) {
        SysDepartmentEntity department = new SysDepartmentEntity();
        department.setId(snowflakeIdGenerator.nextId());
        department.setTenantId(tenantId);
        department.setParentId(null);
        department.setCode(ROOT_DEPARTMENT_CODE);
        department.setName(resolveRootName(rootName));
        department.setSortNo(0);
        department.setEnabled(true);
        return departmentRepository.save(department);
    }

    private String resolveRootName(String rootName) {
        if (rootName == null || rootName.trim().length() == 0) {
            return "默认租户";
        }
        return rootName.trim();
    }
}
