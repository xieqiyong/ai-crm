package com.hz.crm.auth.service;

import com.hz.crm.auth.domain.SysTenantEntity;
import com.hz.crm.auth.repository.SysTenantRepository;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    @Autowired
    private SysTenantRepository tenantRepository;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public SysTenantEntity createInitialTenant(String name) {
        List<SysTenantEntity> tenants = tenantRepository.findByDeletedFalseOrderByCreatedAtAsc();
        if (!tenants.isEmpty()) {
            SysTenantEntity tenant = tenants.get(0);
            tenant.setName(resolveName(name));
            return tenantRepository.save(tenant);
        }
        SysTenantEntity tenant = new SysTenantEntity();
        tenant.setId(snowflakeIdGenerator.nextId());
        tenant.setName(resolveName(name));
        tenant.setCode("tenant-" + tenant.getId());
        tenant.setEnabled(true);
        return tenantRepository.save(tenant);
    }

    private String resolveName(String name) {
        if (name == null || name.trim().length() == 0) {
            return "默认租户";
        }
        return name.trim();
    }
}
