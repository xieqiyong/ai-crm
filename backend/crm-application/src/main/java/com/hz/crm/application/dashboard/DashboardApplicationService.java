package com.hz.crm.application.dashboard;

import com.hz.crm.application.dashboard.dto.DashboardOverviewResponse;
import com.hz.crm.domain.customer.repository.CustomerJpaRepository;
import com.hz.crm.domain.lead.repository.LeadJpaRepository;
import com.hz.crm.domain.opportunity.repository.OpportunityJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardApplicationService {

    @Autowired
    private LeadJpaRepository leadRepository;

    @Autowired
    private CustomerJpaRepository customerRepository;

    @Autowired
    private OpportunityJpaRepository opportunityRepository;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview(String tenantId, Long userId, String dataScope) {
        DashboardOverviewResponse response = new DashboardOverviewResponse();
        if ("SELF".equals(dataScope)) {
            response.setLeadCount(leadRepository.countByTenantIdAndOwnerIdAndDeletedFalse(tenantId, userId));
            response.setCustomerCount(customerRepository.countByTenantIdAndOwnerIdAndDeletedFalse(tenantId, userId));
            response.setOpportunityCount(
                    opportunityRepository.countByTenantIdAndOwnerIdAndDeletedFalse(tenantId, userId));
        } else {
            response.setLeadCount(leadRepository.countByTenantIdAndDeletedFalse(tenantId));
            response.setCustomerCount(customerRepository.countByTenantIdAndDeletedFalse(tenantId));
            response.setOpportunityCount(opportunityRepository.countByTenantIdAndDeletedFalse(tenantId));
        }
        return response;
    }
}
