package com.hz.crm.application.marketing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.CustomerLevel;
import com.hz.crm.domain.customer.CustomerStatus;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.LeadStatus;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.OpportunityStage;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class MarketingFunnelRuleProvider {

    public void applyLeadStage(
            LambdaQueryWrapper<LeadEntity> wrapper, MarketingFunnelStage stage) {
        if (MarketingFunnelStage.ACTIVE_LEAD.equals(stage)) {
            wrapper.in(LeadEntity::getStatus, Arrays.asList(
                    LeadStatus.NEW,
                    LeadStatus.CONTACTED,
                    LeadStatus.FOLLOWING,
                    LeadStatus.QUALIFIED,
                    LeadStatus.NURTURING));
        }
    }

    public void applyCustomerStage(
            LambdaQueryWrapper<CustomerEntity> wrapper, MarketingFunnelStage stage) {
        if (MarketingFunnelStage.TARGET_CUSTOMER.equals(stage)) {
            wrapper.in(CustomerEntity::getLevel, Arrays.asList(
                    CustomerLevel.IMPORTANT,
                    CustomerLevel.STRATEGIC));
            return;
        }
        if (MarketingFunnelStage.CONTRACT_CUSTOMER.equals(stage)) {
            wrapper.eq(CustomerEntity::getStatus, CustomerStatus.COOPERATED);
        }
    }

    public void applyOpportunityStage(
            QueryWrapper<OpportunityEntity> wrapper, MarketingFunnelStage stage) {
        if (MarketingFunnelStage.ACTIVE_OPPORTUNITY.equals(stage)) {
            wrapper.in("stage", Arrays.asList(
                    OpportunityStage.DISCOVERY.name(),
                    OpportunityStage.QUALIFICATION.name(),
                    OpportunityStage.PROPOSAL.name(),
                    OpportunityStage.NEGOTIATION.name()));
        }
    }
}
