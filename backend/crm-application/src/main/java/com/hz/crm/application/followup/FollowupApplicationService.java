package com.hz.crm.application.followup;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.task.SalesTaskApplicationService;
import com.hz.crm.application.followup.dto.FollowupQuery;
import com.hz.crm.application.followup.dto.FollowupResponse;
import com.hz.crm.application.followup.dto.FollowupSaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.followup.FollowupRecordEntity;
import com.hz.crm.domain.followup.FollowupTargetType;
import com.hz.crm.domain.followup.FollowupType;
import com.hz.crm.domain.followup.mapper.FollowupRecordMapper;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.mapper.LeadMapper;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.mapper.OpportunityMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FollowupApplicationService {

    @Autowired
    private FollowupRecordMapper followupRecordMapper;

    @Autowired
    private LeadMapper leadMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private OpportunityMapper opportunityMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired(required = false)
    private UserNameResolver userNameResolver;

    @Autowired
    private SalesTaskApplicationService salesTaskApplicationService;

    @Transactional(readOnly = true)
    public PageData<FollowupResponse> page(Long tenantId, Long userId, String dataScope, FollowupQuery query) {
        FollowupQuery safeQuery = query == null ? new FollowupQuery() : query;
        List<Long> relatedLeadIds = queryRelatedLeadIds(tenantId, safeQuery);
        QueryWrapper<FollowupRecordEntity> countWrapper = buildQueryWrapper(
                tenantId, userId, dataScope, safeQuery, relatedLeadIds);
        long total = followupRecordMapper.selectCount(countWrapper);
        QueryWrapper<FollowupRecordEntity> wrapper = buildQueryWrapper(
                tenantId, userId, dataScope, safeQuery, relatedLeadIds);
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        int offset = (pageNo - 1) * pageSize;
        wrapper.orderByDesc("followup_at").orderByDesc("created_at").last("limit " + pageSize + " offset " + offset);
        List<FollowupRecordEntity> entities = followupRecordMapper.selectList(wrapper);
        List<FollowupResponse> records = new ArrayList<FollowupResponse>();
        for (FollowupRecordEntity entity : entities) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        return PageData.of(total, pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public FollowupResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        FollowupRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        FollowupResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public FollowupResponse save(Long tenantId, Long operatorId, String dataScope, FollowupSaveRequest request) {
        if (request == null) {
            throw new BusinessException("FOLLOWUP_001", "跟进记录不能为空");
        }
        if (request.getTargetType() == null || request.getTargetId() == null) {
            throw new BusinessException("FOLLOWUP_002", "请选择要跟进的对象");
        }
        String content = sanitizeRichText(request.getContent());
        if (!hasRichContent(content)) {
            throw new BusinessException("FOLLOWUP_003", "跟进内容不能为空");
        }
        TargetInfo target = resolveTarget(tenantId, request.getTargetType(), request.getTargetId());
        checkDataScope(operatorId, dataScope, target.getOwnerId());
        FollowupRecordEntity entity;
        boolean newRecord = request.getId() == null;
        LocalDateTime now = DateTimes.now();
        if (newRecord) {
            entity = new FollowupRecordEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setCreatedAt(now);
        } else {
            entity = findOne(tenantId, request.getId());
            checkDataScope(operatorId, dataScope, entity.getOwnerId());
        }
        entity.setUpdatedAt(now);
        entity.setTargetType(request.getTargetType());
        entity.setTargetId(request.getTargetId());
        entity.setTargetName(target.getName());
        entity.setFollowupType(request.getFollowupType() == null ? FollowupType.PHONE : request.getFollowupType());
        entity.setFollowupAt(request.getFollowupAt() == null ? now : request.getFollowupAt());
        entity.setContent(content);
        entity.setResult(trimToNull(request.getResult()));
        entity.setNextPlan(trimToNull(request.getNextPlan()));
        entity.setNextFollowTime(request.getNextFollowTime());
        Long targetOwnerId = request.getOwnerId() == null ? operatorId : request.getOwnerId();
        checkDataScope(operatorId, dataScope, targetOwnerId);
        entity.setOwnerId(targetOwnerId);
        if (newRecord) {
            followupRecordMapper.insert(entity);
        } else {
            followupRecordMapper.updateById(entity);
        }
        salesTaskApplicationService.syncFollowupTask(tenantId, operatorId, dataScope, entity);
        FollowupResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        FollowupRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        entity.setUpdatedAt(DateTimes.now());
        followupRecordMapper.updateById(entity);
    }

    private QueryWrapper<FollowupRecordEntity> buildQueryWrapper(
            Long tenantId,
            Long userId,
            String dataScope,
            FollowupQuery query,
            List<Long> relatedLeadIds) {
        QueryWrapper<FollowupRecordEntity> wrapper = new QueryWrapper<FollowupRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        if ("SELF".equals(dataScope)) {
            wrapper.eq("owner_id", userId);
        }
        if (canMergeCustomerLeadFollowups(query, relatedLeadIds)) {
            wrapper.and(value -> value
                    .eq("target_type", FollowupTargetType.CUSTOMER.name())
                    .eq("target_id", query.getTargetId())
                    .or()
                    .eq("target_type", FollowupTargetType.LEAD.name())
                    .in("target_id", relatedLeadIds));
        } else {
            if (query.getTargetType() != null) {
                wrapper.eq("target_type", query.getTargetType().name());
            }
            if (query.getTargetId() != null) {
                wrapper.eq("target_id", query.getTargetId());
            }
        }
        if (query.getFollowupType() != null) {
            wrapper.eq("followup_type", query.getFollowupType().name());
        }
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .like("target_name", keyword)
                    .or()
                    .like("content", keyword)
                    .or()
                    .like("result", keyword)
                    .or()
                    .like("next_plan", keyword));
        }
        return wrapper;
    }

    private List<Long> queryRelatedLeadIds(Long tenantId, FollowupQuery query) {
        List<Long> leadIds = new ArrayList<Long>();
        if (query == null
                || FollowupTargetType.CUSTOMER != query.getTargetType()
                || query.getTargetId() == null) {
            return leadIds;
        }
        QueryWrapper<LeadEntity> wrapper = new QueryWrapper<LeadEntity>();
        wrapper.select("id");
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.eq("customer_id", query.getTargetId());
        List<LeadEntity> leads = leadMapper.selectList(wrapper);
        for (LeadEntity lead : leads) {
            if (lead.getId() != null) {
                leadIds.add(lead.getId());
            }
        }
        return leadIds;
    }

    private boolean canMergeCustomerLeadFollowups(FollowupQuery query, List<Long> relatedLeadIds) {
        return query != null
                && FollowupTargetType.CUSTOMER == query.getTargetType()
                && query.getTargetId() != null
                && relatedLeadIds != null
                && !relatedLeadIds.isEmpty();
    }

    private FollowupRecordEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("FOLLOWUP_004", "跟进记录编号不能为空");
        }
        QueryWrapper<FollowupRecordEntity> wrapper = new QueryWrapper<FollowupRecordEntity>();
        wrapper.eq("id", id);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        FollowupRecordEntity entity = followupRecordMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("FOLLOWUP_005", "跟进记录不存在");
        }
        return entity;
    }

    private TargetInfo resolveTarget(Long tenantId, FollowupTargetType targetType, Long targetId) {
        if (FollowupTargetType.LEAD == targetType) {
            LeadEntity entity = leadMapper.selectOne(baseTargetWrapper(targetId, tenantId));
            if (entity == null) {
                throw new BusinessException("FOLLOWUP_006", "线索不存在");
            }
            return new TargetInfo(resolveText(entity.getCompanyName(), entity.getName()), entity.getOwnerId());
        }
        if (FollowupTargetType.CUSTOMER == targetType) {
            CustomerEntity entity = customerMapper.selectOne(baseTargetWrapper(targetId, tenantId));
            if (entity == null) {
                throw new BusinessException("FOLLOWUP_007", "客户不存在");
            }
            return new TargetInfo(entity.getName(), entity.getOwnerId());
        }
        if (FollowupTargetType.OPPORTUNITY == targetType) {
            OpportunityEntity entity = opportunityMapper.selectOne(baseTargetWrapper(targetId, tenantId));
            if (entity == null) {
                throw new BusinessException("FOLLOWUP_008", "商机不存在");
            }
            return new TargetInfo(entity.getName(), entity.getOwnerId());
        }
        throw new BusinessException("FOLLOWUP_009", "不支持的跟进对象类型");
    }

    private <T> QueryWrapper<T> baseTargetWrapper(Long targetId, Long tenantId) {
        QueryWrapper<T> wrapper = new QueryWrapper<T>();
        wrapper.eq("id", targetId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        return wrapper;
    }

    private void checkDataScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_001", "无权访问该数据");
        }
    }

    private String resolveText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return trimToNull(second);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String sanitizeRichText(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text
                .replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*\"[^\"]*\"", "")
                .replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*'[^']*'", "")
                .replaceAll("(?i)javascript:", "")
                .trim();
    }

    private boolean hasRichContent(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.matches("(?is).*<img[\\s>].*")) {
            return true;
        }
        String text = value
                .replaceAll("(?is)<[^>]*>", "")
                .replace("&nbsp;", " ")
                .trim();
        return StringUtils.hasText(text);
    }

    private FollowupResponse toResponse(FollowupRecordEntity entity) {
        FollowupResponse response = new FollowupResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setTargetType(entity.getTargetType());
        response.setTargetId(entity.getTargetId());
        response.setTargetName(entity.getTargetName());
        response.setFollowupType(entity.getFollowupType());
        response.setFollowupAt(entity.getFollowupAt());
        response.setContent(entity.getContent());
        response.setResult(entity.getResult());
        response.setNextPlan(entity.getNextPlan());
        response.setNextFollowTime(entity.getNextFollowTime());
        response.setOwnerId(entity.getOwnerId());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private void fillOwnerName(Long tenantId, FollowupResponse response) {
        List<FollowupResponse> records = new ArrayList<FollowupResponse>();
        records.add(response);
        fillOwnerNames(tenantId, records);
    }

    private void fillOwnerNames(Long tenantId, List<FollowupResponse> records) {
        if (userNameResolver == null || records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ownerIds = new HashSet<Long>();
        for (FollowupResponse response : records) {
            if (response.getOwnerId() != null) {
                ownerIds.add(response.getOwnerId());
            }
        }
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userNameResolver.resolve(tenantId, ownerIds);
        for (FollowupResponse response : records) {
            if (response.getOwnerId() != null) {
                response.setOwnerName(names.get(response.getOwnerId()));
            }
        }
    }

    private static class TargetInfo {

        private String name;

        private Long ownerId;

        TargetInfo(String name, Long ownerId) {
            this.name = name;
            this.ownerId = ownerId;
        }

        String getName() {
            return name;
        }

        Long getOwnerId() {
            return ownerId;
        }
    }

}
