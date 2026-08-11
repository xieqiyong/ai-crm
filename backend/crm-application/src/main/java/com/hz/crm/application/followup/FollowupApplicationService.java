package com.hz.crm.application.followup;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.application.followup.dto.FollowupQuery;
import com.hz.crm.application.followup.dto.FollowupResponse;
import com.hz.crm.application.followup.dto.FollowupSaveRequest;
import com.hz.crm.application.followup.dto.FollowupTargetOptionQuery;
import com.hz.crm.application.followup.dto.FollowupTargetOptionResponse;
import com.hz.crm.application.media.MediaTranscriptionApplicationService;
import com.hz.crm.application.media.dto.MediaTranscriptionResponse;
import com.hz.crm.application.task.SalesTaskApplicationService;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.UserDataScopeValidator;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.followup.FollowupRecordEntity;
import com.hz.crm.domain.followup.FollowupObjectProjection;
import com.hz.crm.domain.followup.FollowupTargetType;
import com.hz.crm.domain.followup.FollowupType;
import com.hz.crm.domain.followup.mapper.FollowupRecordMapper;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.LeadStatus;
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

    @Autowired(required = false)
    private UserDataScopeValidator userDataScopeValidator;

    @Autowired
    private SalesTaskApplicationService salesTaskApplicationService;

    @Autowired
    private MediaTranscriptionApplicationService mediaTranscriptionApplicationService;

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
        fillMediaTranscriptions(tenantId, records);
        return PageData.of(total, pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public FollowupResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        FollowupRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        FollowupResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillMediaTranscriptions(tenantId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageData<FollowupResponse> objectPage(Long tenantId, Long userId, String dataScope, FollowupQuery query) {
        FollowupQuery safeQuery = query == null ? new FollowupQuery() : query;
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        long offset = (long) (pageNo - 1) * pageSize;
        String keyword = buildLikeKeyword(safeQuery.getKeyword());
        String targetType = safeQuery.getTargetType() == null ? null : safeQuery.getTargetType().name();
        String followupType = safeQuery.getFollowupType() == null ? null : safeQuery.getFollowupType().name();
        boolean selfScope = "SELF".equals(dataScope);
        Long total = followupRecordMapper.countFollowupObjects(
                tenantId,
                userId,
                selfScope,
                targetType,
                safeQuery.getTargetId(),
                followupType,
                keyword);
        List<FollowupObjectProjection> projections = followupRecordMapper.selectFollowupObjectPage(
                tenantId,
                userId,
                selfScope,
                targetType,
                safeQuery.getTargetId(),
                followupType,
                keyword,
                pageSize,
                offset);
        List<FollowupResponse> records = new ArrayList<FollowupResponse>();
        for (FollowupObjectProjection projection : projections) {
            records.add(toObjectResponse(projection));
        }
        fillOwnerNames(tenantId, records);
        fillMediaTranscriptions(tenantId, records);
        return PageData.of(total == null ? 0L : total.longValue(), pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public List<FollowupTargetOptionResponse> targetOptions(
            Long tenantId, Long userId, String dataScope, FollowupTargetOptionQuery query) {
        FollowupTargetOptionQuery safeQuery = query == null ? new FollowupTargetOptionQuery() : query;
        if (safeQuery.getTargetType() == null) {
            return new ArrayList<FollowupTargetOptionResponse>();
        }
        if (FollowupTargetType.LEAD == safeQuery.getTargetType()) {
            return queryLeadTargetOptions(tenantId, userId, dataScope, safeQuery);
        }
        if (FollowupTargetType.CUSTOMER == safeQuery.getTargetType()) {
            return queryCustomerTargetOptions(tenantId, userId, dataScope, safeQuery);
        }
        if (FollowupTargetType.OPPORTUNITY == safeQuery.getTargetType()) {
            return queryOpportunityTargetOptions(tenantId, userId, dataScope, safeQuery);
        }
        return new ArrayList<FollowupTargetOptionResponse>();
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
            updateLeadStatusAfterFollowup(tenantId, entity.getTargetType(), entity.getTargetId(), now);
        } else {
            followupRecordMapper.updateById(entity);
        }
        salesTaskApplicationService.syncFollowupTask(tenantId, operatorId, dataScope, entity);
        FollowupResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillMediaTranscriptions(tenantId, response);
        return response;
    }

    private void updateLeadStatusAfterFollowup(Long tenantId, FollowupTargetType targetType, Long targetId, LocalDateTime now) {
        if (FollowupTargetType.LEAD != targetType || targetId == null) {
            return;
        }
        leadMapper.update(null, Wrappers.<LeadEntity>lambdaUpdate()
                .eq(LeadEntity::getTenantId, tenantId)
                .eq(LeadEntity::getId, targetId)
                .eq(LeadEntity::isDeleted, false)
                .eq(LeadEntity::getStatus, LeadStatus.NEW)
                .set(LeadEntity::getStatus, LeadStatus.FOLLOWING)
                .set(LeadEntity::getUpdatedAt, now));
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

    private List<FollowupTargetOptionResponse> queryLeadTargetOptions(
            Long tenantId, Long userId, String dataScope, FollowupTargetOptionQuery query) {
        QueryWrapper<LeadEntity> wrapper = new QueryWrapper<LeadEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        appendOwnerScopeToTargetWrapper(wrapper, tenantId, userId, dataScope);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .like("name", keyword)
                    .or()
                    .like("company_name", keyword)
                    .or()
                    .like("phone", keyword));
        }
        wrapper.orderByDesc("created_at").last("limit " + safeOptionLimit(query.getLimit()));
        List<LeadEntity> entities = leadMapper.selectList(wrapper);
        List<FollowupTargetOptionResponse> options = new ArrayList<FollowupTargetOptionResponse>();
        for (LeadEntity entity : entities) {
            FollowupTargetOptionResponse option = new FollowupTargetOptionResponse();
            option.setId(entity.getId());
            option.setTargetType(FollowupTargetType.LEAD);
            option.setName(resolveText(entity.getCompanyName(), entity.getName()));
            option.setDescription(resolveDescription("线索", entity.getName(), entity.getPhone()));
            option.setOwnerId(entity.getOwnerId());
            options.add(option);
        }
        fillTargetOwnerNames(tenantId, options);
        return options;
    }

    private List<FollowupTargetOptionResponse> queryCustomerTargetOptions(
            Long tenantId, Long userId, String dataScope, FollowupTargetOptionQuery query) {
        QueryWrapper<CustomerEntity> wrapper = new QueryWrapper<CustomerEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        appendOwnerScopeToTargetWrapper(wrapper, tenantId, userId, dataScope);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .like("name", keyword)
                    .or()
                    .like("contact_name", keyword)
                    .or()
                    .like("contact_phone", keyword));
        }
        wrapper.orderByDesc("created_at").last("limit " + safeOptionLimit(query.getLimit()));
        List<CustomerEntity> entities = customerMapper.selectList(wrapper);
        List<FollowupTargetOptionResponse> options = new ArrayList<FollowupTargetOptionResponse>();
        for (CustomerEntity entity : entities) {
            FollowupTargetOptionResponse option = new FollowupTargetOptionResponse();
            option.setId(entity.getId());
            option.setTargetType(FollowupTargetType.CUSTOMER);
            option.setName(entity.getName());
            option.setDescription(resolveDescription("客户", entity.getContactName(), entity.getContactPhone()));
            option.setOwnerId(entity.getOwnerId());
            options.add(option);
        }
        fillTargetOwnerNames(tenantId, options);
        return options;
    }

    private List<FollowupTargetOptionResponse> queryOpportunityTargetOptions(
            Long tenantId, Long userId, String dataScope, FollowupTargetOptionQuery query) {
        QueryWrapper<OpportunityEntity> wrapper = new QueryWrapper<OpportunityEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        appendOwnerScopeToTargetWrapper(wrapper, tenantId, userId, dataScope);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .like("name", keyword)
                    .or()
                    .like("remark", keyword));
        }
        wrapper.orderByDesc("created_at").last("limit " + safeOptionLimit(query.getLimit()));
        List<OpportunityEntity> entities = opportunityMapper.selectList(wrapper);
        List<FollowupTargetOptionResponse> options = new ArrayList<FollowupTargetOptionResponse>();
        for (OpportunityEntity entity : entities) {
            FollowupTargetOptionResponse option = new FollowupTargetOptionResponse();
            option.setId(entity.getId());
            option.setTargetType(FollowupTargetType.OPPORTUNITY);
            option.setName(entity.getName());
            option.setDescription(resolveDescription("商机", entity.getStage() == null ? null : entity.getStage().name(), null));
            option.setOwnerId(entity.getOwnerId());
            options.add(option);
        }
        fillTargetOwnerNames(tenantId, options);
        return options;
    }

    private <T> void appendOwnerScopeToTargetWrapper(
            QueryWrapper<T> wrapper, Long tenantId, Long userId, String dataScope) {
        if (userDataScopeValidator == null) {
            if ("SELF".equals(dataScope)) {
                wrapper.eq("owner_id", userId);
            }
            return;
        }
        if ("ALL".equals(dataScope)) {
            return;
        }
        List<Long> userIds = userDataScopeValidator.listAccessibleUserIds(tenantId, userId, dataScope);
        if (userIds == null || userIds.isEmpty()) {
            wrapper.eq("owner_id", -1L);
            return;
        }
        wrapper.in("owner_id", userIds);
    }

    private int safeOptionLimit(Integer limit) {
        if (limit == null || limit.intValue() < 1) {
            return 20;
        }
        return Math.min(limit.intValue(), 50);
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

    private String resolveDescription(String type, String first, String second) {
        List<String> parts = new ArrayList<String>();
        if (StringUtils.hasText(type)) {
            parts.add(type.trim());
        }
        if (StringUtils.hasText(first)) {
            parts.add(first.trim());
        }
        if (StringUtils.hasText(second)) {
            parts.add(second.trim());
        }
        return String.join(" ｜ ", parts);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String buildLikeKeyword(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return "%" + text.toLowerCase() + "%";
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

    private FollowupResponse toObjectResponse(FollowupObjectProjection projection) {
        FollowupResponse response = new FollowupResponse();
        response.setId(projection.getId());
        response.setTenantId(projection.getTenantId());
        response.setTargetType(projection.getTargetType());
        response.setTargetId(projection.getTargetId());
        response.setTargetName(projection.getTargetName());
        response.setFollowupType(projection.getFollowupType());
        response.setFollowupAt(projection.getFollowupAt());
        response.setContent(projection.getContent());
        response.setResult(projection.getResult());
        response.setNextPlan(projection.getNextPlan());
        response.setNextFollowTime(projection.getNextFollowTime());
        response.setOwnerId(projection.getOwnerId());
        response.setFollowupCount(projection.getFollowupCount());
        response.setCreatedAt(projection.getCreatedAt());
        response.setUpdatedAt(projection.getUpdatedAt());
        return response;
    }

    private void fillOwnerName(Long tenantId, FollowupResponse response) {
        List<FollowupResponse> records = new ArrayList<FollowupResponse>();
        records.add(response);
        fillOwnerNames(tenantId, records);
    }

    private void fillMediaTranscriptions(Long tenantId, FollowupResponse response) {
        List<FollowupResponse> records = new ArrayList<FollowupResponse>();
        records.add(response);
        fillMediaTranscriptions(tenantId, records);
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

    private void fillTargetOwnerNames(Long tenantId, List<FollowupTargetOptionResponse> options) {
        if (userNameResolver == null || options == null || options.isEmpty()) {
            return;
        }
        Set<Long> ownerIds = new HashSet<Long>();
        for (FollowupTargetOptionResponse option : options) {
            if (option.getOwnerId() != null) {
                ownerIds.add(option.getOwnerId());
            }
        }
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userNameResolver.resolve(tenantId, ownerIds);
        for (FollowupTargetOptionResponse option : options) {
            if (option.getOwnerId() != null) {
                option.setOwnerName(names.get(option.getOwnerId()));
            }
        }
    }

    private void fillMediaTranscriptions(Long tenantId, List<FollowupResponse> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> followupIds = new HashSet<Long>();
        for (FollowupResponse response : records) {
            if (response.getId() != null) {
                followupIds.add(response.getId());
            }
        }
        if (followupIds.isEmpty()) {
            return;
        }
        Map<Long, List<MediaTranscriptionResponse>> grouped =
                mediaTranscriptionApplicationService.groupByFollowupIds(tenantId, followupIds);
        for (FollowupResponse response : records) {
            List<MediaTranscriptionResponse> mediaItems = grouped.get(response.getId());
            if (mediaItems != null) {
                response.setMediaTranscriptions(mediaItems);
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
