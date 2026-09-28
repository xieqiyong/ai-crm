package com.hz.crm.application.marketing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.application.marketing.dto.MarketingFunnelItem;
import com.hz.crm.common.user.UserDataScopeValidator;
import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.mapper.ChannelRecordMapper;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.mapper.LeadMapper;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.mapper.OpportunityMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketingFunnelApplicationService {

    @Autowired
    private ChannelRecordMapper channelRecordMapper;

    @Autowired
    private LeadMapper leadMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private OpportunityMapper opportunityMapper;

    @Autowired
    private UserDataScopeValidator userDataScopeValidator;

    @Autowired
    private MarketingFunnelRuleProvider marketingFunnelRuleProvider;

    @Transactional(readOnly = true)
    public List<MarketingFunnelItem> overview(
            Long tenantId,
            Long userId,
            String dataScope,
            List<String> permissions,
            List<String> menuPermissions) {
        boolean scopeRestricted = !"ALL".equals(dataScope);
        List<Long> ownerIds = scopeRestricted
                ? userDataScopeValidator.listAccessibleUserIds(tenantId, userId, dataScope)
                : new ArrayList<Long>();
        List<MarketingFunnelItem> items = new ArrayList<MarketingFunnelItem>();
        items.add(buildPublicPoolItem(tenantId, permissions, menuPermissions));
        items.add(buildLeadItem(tenantId, scopeRestricted, ownerIds, permissions, menuPermissions));
        items.add(buildCustomerItem(
                tenantId, scopeRestricted, ownerIds, MarketingFunnelStage.CUSTOMER,
                "客户", "当前全部客户", "customers?funnelStage=CUSTOMER",
                permissions, menuPermissions));
        items.add(buildCustomerItem(
                tenantId, scopeRestricted, ownerIds, MarketingFunnelStage.TARGET_CUSTOMER,
                "目标客户", "重点客户与战略客户",
                "customers?funnelStage=TARGET_CUSTOMER&levels=IMPORTANT,STRATEGIC",
                permissions, menuPermissions));
        items.add(buildOpportunityItem(
                tenantId, scopeRestricted, ownerIds, permissions, menuPermissions));
        items.add(buildCustomerItem(
                tenantId, scopeRestricted, ownerIds, MarketingFunnelStage.CONTRACT_CUSTOMER,
                "合同用户", "当前按已合作客户统计", "customers?funnelStage=CONTRACT_CUSTOMER",
                permissions, menuPermissions));
        return items;
    }

    private MarketingFunnelItem buildPublicPoolItem(
            Long tenantId, List<String> permissions, List<String> menuPermissions) {
        boolean accessible = canAccess(
                permissions, menuPermissions, "crm:public-pool:view", "menu.public_pool");
        Long count = null;
        if (accessible) {
            QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
            wrapper.eq("tenant_id", tenantId);
            wrapper.eq("deleted", false);
            wrapper.isNull("lead_id");
            count = safeCount(channelRecordMapper.selectCount(wrapper));
        }
        return buildItem(
                MarketingFunnelStage.PUBLIC_POOL,
                "公海",
                "尚未分配并晋升线索的数据",
                count,
                "public-pool?funnelStage=PUBLIC_POOL",
                accessible);
    }

    private MarketingFunnelItem buildLeadItem(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            List<String> permissions,
            List<String> menuPermissions) {
        boolean accessible = canAccess(permissions, menuPermissions, "crm:lead:view", "menu.leads");
        Long count = null;
        if (accessible) {
            LambdaQueryWrapper<LeadEntity> wrapper = Wrappers.<LeadEntity>lambdaQuery()
                    .eq(LeadEntity::getTenantId, tenantId)
                    .eq(LeadEntity::isDeleted, false);
            applyLeadOwnerScope(wrapper, scopeRestricted, ownerIds);
            marketingFunnelRuleProvider.applyLeadStage(wrapper, MarketingFunnelStage.ACTIVE_LEAD);
            count = safeCount(leadMapper.selectCount(wrapper));
        }
        return buildItem(
                MarketingFunnelStage.ACTIVE_LEAD,
                "线索",
                "尚未转化的有效线索",
                count,
                "leads?funnelStage=ACTIVE_LEAD",
                accessible);
    }

    private MarketingFunnelItem buildCustomerItem(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            MarketingFunnelStage stage,
            String name,
            String description,
            String targetRoute,
            List<String> permissions,
            List<String> menuPermissions) {
        boolean accessible = canAccess(
                permissions, menuPermissions, "crm:customer:view", "menu.customers");
        Long count = null;
        if (accessible) {
            LambdaQueryWrapper<CustomerEntity> wrapper = Wrappers.<CustomerEntity>lambdaQuery()
                    .eq(CustomerEntity::getTenantId, tenantId)
                    .eq(CustomerEntity::isDeleted, false);
            applyCustomerOwnerScope(wrapper, scopeRestricted, ownerIds);
            marketingFunnelRuleProvider.applyCustomerStage(wrapper, stage);
            count = safeCount(customerMapper.selectCount(wrapper));
        }
        return buildItem(stage, name, description, count, targetRoute, accessible);
    }

    private MarketingFunnelItem buildOpportunityItem(
            Long tenantId,
            boolean scopeRestricted,
            List<Long> ownerIds,
            List<String> permissions,
            List<String> menuPermissions) {
        boolean accessible = canAccess(
                permissions, menuPermissions, "crm:opportunity:view", "menu.opportunities");
        Long count = null;
        if (accessible) {
            QueryWrapper<OpportunityEntity> wrapper = new QueryWrapper<OpportunityEntity>();
            wrapper.eq("tenant_id", tenantId);
            wrapper.eq("deleted", false);
            applyOwnerScope(wrapper, scopeRestricted, ownerIds);
            marketingFunnelRuleProvider.applyOpportunityStage(
                    wrapper, MarketingFunnelStage.ACTIVE_OPPORTUNITY);
            count = safeCount(opportunityMapper.selectCount(wrapper));
        }
        return buildItem(
                MarketingFunnelStage.ACTIVE_OPPORTUNITY,
                "已推进商机",
                "正在推进且尚未结束的商机",
                count,
                "opportunities?funnelStage=ACTIVE_OPPORTUNITY",
                accessible);
    }

    private MarketingFunnelItem buildItem(
            MarketingFunnelStage code,
            String name,
            String description,
            Long count,
            String targetRoute,
            boolean accessible) {
        MarketingFunnelItem item = new MarketingFunnelItem();
        item.setCode(code);
        item.setName(name);
        item.setDescription(description);
        item.setCount(count);
        item.setTargetRoute(targetRoute);
        item.setAccessible(accessible);
        return item;
    }

    private boolean canAccess(
            List<String> permissions,
            List<String> menuPermissions,
            String actionPermission,
            String menuPermission) {
        if (permissions != null && permissions.contains("*")) {
            return true;
        }
        return permissions != null
                && permissions.contains(actionPermission)
                && menuPermissions != null
                && menuPermissions.contains(menuPermission);
    }

    private void applyLeadOwnerScope(
            LambdaQueryWrapper<LeadEntity> wrapper,
            boolean scopeRestricted,
            List<Long> ownerIds) {
        if (!scopeRestricted) {
            return;
        }
        if (ownerIds == null || ownerIds.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in(LeadEntity::getOwnerId, ownerIds);
        }
    }

    private void applyCustomerOwnerScope(
            LambdaQueryWrapper<CustomerEntity> wrapper,
            boolean scopeRestricted,
            List<Long> ownerIds) {
        if (!scopeRestricted) {
            return;
        }
        if (ownerIds == null || ownerIds.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in(CustomerEntity::getOwnerId, ownerIds);
        }
    }

    private void applyOwnerScope(
            QueryWrapper<?> wrapper, boolean scopeRestricted, List<Long> ownerIds) {
        if (!scopeRestricted) {
            return;
        }
        if (ownerIds == null || ownerIds.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in("owner_id", ownerIds);
        }
    }

    private Long safeCount(Long count) {
        return count == null ? Long.valueOf(0L) : count;
    }
}
