package com.hz.crm.application.channel;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.channel.dto.ChannelDocumentImportRequest;
import com.hz.crm.application.channel.dto.ChannelBatchAssignRequest;
import com.hz.crm.application.channel.dto.ChannelMediaImportRequest;
import com.hz.crm.application.channel.dto.ChannelPromoteRequest;
import com.hz.crm.application.channel.dto.ChannelQuery;
import com.hz.crm.application.channel.dto.ChannelResponse;
import com.hz.crm.application.channel.dto.ChannelSaveRequest;
import com.hz.crm.application.channel.dto.ChannelStatisticsResponse;
import com.hz.crm.application.channel.dto.ExternalChannelSyncRequest;
import com.hz.crm.application.channel.dto.ExternalChannelSyncResult;
import com.hz.crm.application.lead.LeadApplicationService;
import com.hz.crm.application.lead.dto.LeadResponse;
import com.hz.crm.application.lead.dto.LeadSaveRequest;
import com.hz.crm.application.product.ProductReferenceResolver;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.AssignableUserResolver;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.ChannelSource;
import com.hz.crm.domain.channel.ChannelStatus;
import com.hz.crm.domain.channel.ChannelType;
import com.hz.crm.domain.channel.mapper.ChannelRecordMapper;
import com.hz.crm.domain.lead.LeadStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChannelApplicationService {

    @Autowired
    private ChannelRecordMapper channelRecordMapper;

    @Autowired
    private LeadApplicationService leadApplicationService;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired(required = false)
    private UserNameResolver userNameResolver;

    @Autowired
    private AssignableUserResolver assignableUserResolver;

    @Autowired
    private ProductReferenceResolver productReferenceResolver;

    @Transactional(readOnly = true)
    public PageData<ChannelResponse> page(Long tenantId, Long userId, String dataScope, ChannelQuery query) {
        ChannelQuery safeQuery = query == null ? new ChannelQuery() : query;
        long total = channelRecordMapper.selectCount(buildQueryWrapper(tenantId, userId, dataScope, safeQuery));
        QueryWrapper<ChannelRecordEntity> wrapper = buildQueryWrapper(tenantId, userId, dataScope, safeQuery);
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        int offset = (pageNo - 1) * pageSize;
        wrapper.orderByDesc("created_at").last("limit " + pageSize + " offset " + offset);
        List<ChannelRecordEntity> entities = channelRecordMapper.selectList(wrapper);
        List<ChannelResponse> records = new ArrayList<ChannelResponse>();
        for (ChannelRecordEntity entity : entities) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        fillProductNames(tenantId, records);
        return PageData.of(total, pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public PageData<ChannelResponse> publicPoolPage(Long tenantId, ChannelQuery query) {
        ChannelQuery safeQuery = query == null ? new ChannelQuery() : query;
        QueryWrapper<ChannelRecordEntity> countWrapper = buildQueryWrapper(
                tenantId, null, "ALL", safeQuery);
        countWrapper.isNull("lead_id");
        long total = channelRecordMapper.selectCount(countWrapper);
        QueryWrapper<ChannelRecordEntity> wrapper = buildQueryWrapper(
                tenantId, null, "ALL", safeQuery);
        wrapper.isNull("lead_id");
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        int offset = (pageNo - 1) * pageSize;
        wrapper.orderByDesc("created_at").orderByDesc("id")
                .last("limit " + pageSize + " offset " + offset);
        List<ChannelRecordEntity> entities = channelRecordMapper.selectList(wrapper);
        List<ChannelResponse> records = new ArrayList<ChannelResponse>();
        for (ChannelRecordEntity entity : entities) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        fillProductNames(tenantId, records);
        return PageData.of(total, pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public ChannelStatisticsResponse statistics(Long tenantId, Long userId, String dataScope) {
        LocalDateTime dayStart = DateTimes.now().toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        ChannelStatisticsResponse response = new ChannelStatisticsResponse();
        response.setTotalUserCount(channelRecordMapper.selectCount(buildStatisticsWrapper(tenantId, userId, dataScope)));
        QueryWrapper<ChannelRecordEntity> todayWrapper = buildStatisticsWrapper(tenantId, userId, dataScope);
        todayWrapper.ge("created_at", dayStart);
        todayWrapper.lt("created_at", dayEnd);
        response.setTodayNewUserCount(channelRecordMapper.selectCount(todayWrapper));
        QueryWrapper<ChannelRecordEntity> convertedWrapper = buildStatisticsWrapper(tenantId, userId, dataScope);
        convertedWrapper.isNotNull("lead_id");
        response.setConvertedLeadUserCount(channelRecordMapper.selectCount(convertedWrapper));
        return response;
    }

    @Transactional(readOnly = true)
    public ChannelResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse save(Long tenantId, Long operatorId, String dataScope, ChannelSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BusinessException("CHANNEL_001", "渠道标题不能为空");
        }
        ChannelRecordEntity entity;
        LocalDateTime now = DateTimes.now();
        if (request.getId() == null) {
            entity = new ChannelRecordEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setOwnerId(operatorId);
            entity.setStatus(ChannelStatus.NEW);
            entity.setCreatedAt(now);
        } else {
            entity = findOne(tenantId, request.getId());
            checkDataScope(operatorId, dataScope, entity.getOwnerId());
        }
        entity.setUpdatedAt(now);
        entity.setTitle(trimToNull(request.getTitle()));
        entity.setChannelType(request.getChannelType() == null ? ChannelType.MANUAL : request.getChannelType());
        entity.setSource(normalizeSource(request.getSource()));
        entity.setContactName(trimToNull(request.getContactName()));
        entity.setCompanyName(trimToNull(request.getCompanyName()));
        entity.setPhone(trimToNull(request.getPhone()));
        entity.setEmail(trimToNull(request.getEmail()));
        if (!requiresAiAnalysis(entity)) {
            entity.setRemark(trimToNull(request.getRemark()));
        }
        if (request.getId() == null) {
            channelRecordMapper.insert(entity);
        } else {
            channelRecordMapper.updateById(entity);
        }
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ExternalChannelSyncResult syncExternalChannel(Long tenantId, ExternalChannelSyncRequest request) {
        validateExternalSyncRequest(request);
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("external_provider", trimToNull(request.getExternalProvider()));
        wrapper.eq("external_key", trimToNull(request.getExternalKey()));
        ChannelRecordEntity entity = channelRecordMapper.selectOne(wrapper);
        if (entity == null) {
            entity = findDuplicateExternalChannel(tenantId, request);
        }
        ExternalChannelSyncResult result = new ExternalChannelSyncResult();
        LocalDateTime now = DateTimes.now();
        if (entity == null) {
            entity = new ChannelRecordEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setDeleted(false);
            entity.setCreatedAt(request.getOccurredAt() == null ? now : request.getOccurredAt());
            entity.setStatus(ChannelStatus.NEW);
            entity.setChannelType(ChannelType.WECOM);
            entity.setExternalProvider(trimToNull(request.getExternalProvider()));
            entity.setExternalKey(trimToNull(request.getExternalKey()));
            applyExternalChannel(entity, request);
            entity.setUpdatedAt(now);
            channelRecordMapper.insert(entity);
            result.setCreated(true);
        } else if (entity.isDeleted()) {
            result.setSkipped(true);
        } else {
            boolean changed = hasExternalChannelChanges(entity, request);
            if (changed && entity.getLeadId() == null) {
                resetExternalAnalysis(entity);
                applyExternalChannel(entity, request);
                entity.setStatus(ChannelStatus.NEW);
                entity.setUpdatedAt(now);
                channelRecordMapper.updateById(entity);
                result.setUpdated(true);
            } else {
                result.setSkipped(true);
            }
        }
        result.setChannelId(entity.getId());
        return result;
    }

    @Transactional
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        entity.setUpdatedAt(DateTimes.now());
        channelRecordMapper.updateById(entity);
    }

    @Transactional
    public ChannelResponse importMedia(Long tenantId, Long operatorId, ChannelMediaImportRequest request) {
        if (request == null || !StringUtils.hasText(request.getMediaFileName())) {
            throw new BusinessException("CHANNEL_002", "请上传录音或视频文件");
        }
        ChannelRecordEntity entity = new ChannelRecordEntity();
        LocalDateTime now = DateTimes.now();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(tenantId);
        entity.setOwnerId(operatorId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setTitle(resolveMediaTitle(request));
        entity.setChannelType(resolveMediaType(request));
        entity.setStatus(ChannelStatus.WAITING_TRANSCRIPTION);
        entity.setSource(normalizeSource(request.getSource()));
        entity.setContactName(trimToNull(request.getContactName()));
        entity.setCompanyName(trimToNull(request.getCompanyName()));
        entity.setPhone(trimToNull(request.getPhone()));
        entity.setEmail(trimToNull(request.getEmail()));
        entity.setMediaFileName(trimToNull(request.getMediaFileName()));
        entity.setMediaContentType(trimToNull(request.getMediaContentType()));
        entity.setMediaSize(request.getMediaSize());
        entity.setMediaStorageKey(trimToNull(request.getMediaStorageKey()));
        entity.setRemark(null);
        channelRecordMapper.insert(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse importDocument(Long tenantId, Long operatorId, ChannelDocumentImportRequest request) {
        if (request == null || !StringUtils.hasText(request.getMediaFileName())) {
            throw new BusinessException("CHANNEL_010", "请上传文档或HTML页面");
        }
        if (!StringUtils.hasText(request.getDocumentText())) {
            throw new BusinessException("CHANNEL_011", "文档内容为空，无法提取关键信息");
        }
        ChannelDocumentProfile profile = extractDocumentProfile(request.getDocumentText());
        ChannelRecordEntity entity = new ChannelRecordEntity();
        LocalDateTime now = DateTimes.now();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(tenantId);
        entity.setOwnerId(operatorId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setTitle(firstText(request.getTitle(), profile.getTitle(), request.getMediaFileName()));
        entity.setChannelType(ChannelType.DOCUMENT);
        entity.setStatus(ChannelStatus.WAITING_AI_ANALYSIS);
        entity.setSource(normalizeSource(request.getSource()));
        entity.setContactName(firstText(request.getContactName(), profile.getContactName()));
        entity.setCompanyName(firstText(request.getCompanyName(), profile.getCompanyName()));
        entity.setPhone(firstText(request.getPhone(), profile.getPhone()));
        entity.setEmail(firstText(request.getEmail(), profile.getEmail()));
        entity.setMediaFileName(trimToNull(request.getMediaFileName()));
        entity.setMediaContentType(trimToNull(request.getMediaContentType()));
        entity.setMediaSize(request.getMediaSize());
        entity.setMediaStorageKey(trimToNull(request.getMediaStorageKey()));
        entity.setTranscriptText(limitText(request.getDocumentText(), 12000));
        entity.setAiSummary(null);
        entity.setUsefulInfo(null);
        entity.setAiAnalysisJson(null);
        entity.setAgentRunId(null);
        entity.setAiAnalyzedAt(null);
        entity.setRemark(null);
        channelRecordMapper.insert(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse prepareTranscription(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        checkNotPromoted(entity);
        entity.setStatus(ChannelStatus.WAITING_TRANSCRIPTION);
        entity.setUpdatedAt(DateTimes.now());
        channelRecordMapper.updateById(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse prepareAiAnalysis(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        checkNotPromoted(entity);
        if (isImportedMaterial(entity) && !StringUtils.hasText(entity.getTranscriptText())) {
            throw new BusinessException("CHANNEL_014", "渠道材料尚未完成中文转译，不能进行AI分析");
        }
        entity.setStatus(ChannelStatus.WAITING_AI_ANALYSIS);
        entity.setUpdatedAt(DateTimes.now());
        channelRecordMapper.updateById(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse completeAiAnalysis(
            Long tenantId,
            Long userId,
            String dataScope,
            Long id,
            String summary,
            String usefulInfo,
            String remark,
            String analysisJson,
            Long agentRunId) {
        if (!StringUtils.hasText(remark)) {
            throw new BusinessException("CHANNEL_AI_009", "渠道智能体未生成有效备注");
        }
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        checkNotPromoted(entity);
        entity.setAiSummary(trimToNull(summary));
        entity.setUsefulInfo(trimToNull(usefulInfo));
        entity.setRemark(trimToNull(remark));
        entity.setAiAnalysisJson(trimToNull(analysisJson));
        entity.setAgentRunId(agentRunId);
        entity.setAiAnalyzedAt(DateTimes.now());
        entity.setStatus(ChannelStatus.ANALYZED);
        entity.setUpdatedAt(DateTimes.now());
        channelRecordMapper.updateById(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse promoteToLead(
            Long tenantId, Long operatorId, String dataScope, ChannelPromoteRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException("CHANNEL_003", "渠道编号不能为空");
        }
        ChannelRecordEntity entity = findOne(tenantId, request.getId());
        checkDataScope(operatorId, dataScope, entity.getOwnerId());
        if (entity.getLeadId() != null) {
            throw new BusinessException("CHANNEL_004", "渠道已晋升为线索");
        }
        if (!isPromotionReady(entity)) {
            throw new BusinessException("CHANNEL_015", resolvePromotionBlockReason(entity));
        }
        return promoteEntity(tenantId, operatorId, dataScope, request, entity);
    }

    @Transactional
    public ChannelResponse assignPublicPool(
            Long tenantId, Long operatorId, String dataScope, ChannelPromoteRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException("PUBLIC_POOL_001", "公海数据编号不能为空");
        }
        if (request.getOwnerId() == null) {
            throw new BusinessException("PUBLIC_POOL_002", "请选择要分配的销售负责人");
        }
        ChannelRecordEntity entity = findOne(tenantId, request.getId());
        if (entity.getLeadId() != null) {
            throw new BusinessException("PUBLIC_POOL_003", "该公海数据已经完成分配");
        }
        if (!isPromotionReady(entity)) {
            throw new BusinessException("PUBLIC_POOL_004", resolvePromotionBlockReason(entity));
        }
        assignableUserResolver.resolveAssignableName(
                tenantId, operatorId, dataScope, request.getOwnerId());
        return promoteEntity(tenantId, operatorId, dataScope, request, entity);
    }

    @Transactional
    public List<ChannelResponse> assignPublicPoolBatch(
            Long tenantId,
            Long operatorId,
            String dataScope,
            ChannelBatchAssignRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new BusinessException("PUBLIC_POOL_BATCH_001", "请选择需要分配的公海数据");
        }
        if (request.getOwnerId() == null) {
            throw new BusinessException("PUBLIC_POOL_BATCH_002", "请选择要分配的销售负责人");
        }
        Set<Long> uniqueIds = new LinkedHashSet<Long>(request.getIds());
        if (uniqueIds.size() > 100) {
            throw new BusinessException("PUBLIC_POOL_BATCH_003", "单次最多分配100条公海数据");
        }
        if (uniqueIds.contains(null)) {
            throw new BusinessException("PUBLIC_POOL_BATCH_004", "公海数据编号不能为空");
        }
        assignableUserResolver.resolveAssignableName(
                tenantId, operatorId, dataScope, request.getOwnerId());
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.in("id", uniqueIds);
        List<ChannelRecordEntity> entities = channelRecordMapper.selectList(wrapper);
        Map<Long, ChannelRecordEntity> entityMap = new HashMap<Long, ChannelRecordEntity>();
        for (ChannelRecordEntity entity : entities) {
            entityMap.put(entity.getId(), entity);
        }
        for (Long id : uniqueIds) {
            ChannelRecordEntity entity = entityMap.get(id);
            if (entity == null) {
                throw new BusinessException("PUBLIC_POOL_BATCH_005", "部分公海数据不存在或已被删除");
            }
            if (entity.getLeadId() != null) {
                throw new BusinessException("PUBLIC_POOL_BATCH_006", "部分公海数据已经完成分配，请刷新后重试");
            }
            if (!isPromotionReady(entity)) {
                throw new BusinessException("PUBLIC_POOL_BATCH_007", resolvePromotionBlockReason(entity));
            }
        }
        List<ChannelResponse> responses = new ArrayList<ChannelResponse>();
        for (Long id : uniqueIds) {
            ChannelPromoteRequest promoteRequest = new ChannelPromoteRequest();
            promoteRequest.setId(id);
            promoteRequest.setOwnerId(request.getOwnerId());
            responses.add(promoteEntity(
                    tenantId, operatorId, dataScope, promoteRequest, entityMap.get(id)));
        }
        return responses;
    }

    private ChannelResponse promoteEntity(
            Long tenantId,
            Long operatorId,
            String dataScope,
            ChannelPromoteRequest request,
            ChannelRecordEntity entity) {
        LeadSaveRequest leadRequest = new LeadSaveRequest();
        leadRequest.setName(resolveLeadName(entity));
        leadRequest.setCompanyName(entity.getCompanyName());
        leadRequest.setPhone(entity.getPhone());
        leadRequest.setEmail(entity.getEmail());
        leadRequest.setSource(resolveLeadSource(entity));
        leadRequest.setStatus(LeadStatus.recommended());
        leadRequest.setOwnerId(request.getOwnerId() == null ? entity.getOwnerId() : request.getOwnerId());
        leadRequest.setProductId(entity.getProductId());
        leadRequest.setRemark(resolveLeadRemark(entity));
        LeadResponse lead = leadApplicationService.createFromPublicPool(
                tenantId, operatorId, dataScope, leadRequest);
        entity.setLeadId(lead.getId());
        entity.setStatus(ChannelStatus.PROMOTED);
        entity.setUpdatedAt(DateTimes.now());
        channelRecordMapper.updateById(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    private QueryWrapper<ChannelRecordEntity> buildQueryWrapper(
            Long tenantId, Long userId, String dataScope, ChannelQuery query) {
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        if ("SELF".equals(dataScope)) {
            wrapper.eq("owner_id", userId);
        }
        if (query.getStatus() != null) {
            wrapper.eq("status", query.getStatus().name());
        }
        if (query.getChannelType() != null) {
            wrapper.eq("channel_type", query.getChannelType().name());
        }
        if (query.getCreatedFrom() != null) {
            wrapper.ge("created_at", query.getCreatedFrom());
        }
        if (query.getCreatedTo() != null) {
            wrapper.le("created_at", query.getCreatedTo());
        }
        String keyword = likeKeyword(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .apply("lower(coalesce(title, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(source, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(contact_name, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(company_name, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(phone, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(email, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(ai_summary, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(useful_info, '')) like {0}", keyword));
        }
        return wrapper;
    }

    private QueryWrapper<ChannelRecordEntity> buildStatisticsWrapper(Long tenantId, Long userId, String dataScope) {
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        if ("SELF".equals(dataScope)) {
            wrapper.eq("owner_id", userId);
        }
        return wrapper;
    }

    private ChannelRecordEntity findDuplicateExternalChannel(
            Long tenantId, ExternalChannelSyncRequest request) {
        String phone = normalizePhone(request.getPhone());
        String companyName = normalizeCompanyName(request.getCompanyName());
        if (phone == null && companyName == null) {
            return null;
        }
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        if (request.getProductId() != null) {
            wrapper.eq("product_id", request.getProductId());
        }
        wrapper.and(condition -> {
            boolean hasPhone = phone != null;
            if (hasPhone) {
                condition.apply(
                        "regexp_replace(coalesce(phone, ''), '[^0-9]', '', 'g') = {0}", phone);
            }
            if (companyName != null) {
                if (hasPhone) {
                    condition.or();
                }
                condition.apply(
                        "regexp_replace(lower(coalesce(company_name, '')), '\\s+', '', 'g') = {0}",
                        companyName);
            }
        });
        wrapper.orderByAsc("created_at").orderByAsc("id").last("limit 1");
        return channelRecordMapper.selectOne(wrapper);
    }

    private ChannelRecordEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("CHANNEL_003", "渠道编号不能为空");
        }
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("id", id);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        ChannelRecordEntity entity = channelRecordMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("CHANNEL_005", "渠道记录不存在");
        }
        return entity;
    }

    private void checkDataScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_001", "无权访问该渠道记录");
        }
    }

    private void checkNotPromoted(ChannelRecordEntity entity) {
        if (entity.getLeadId() != null) {
            throw new BusinessException("CHANNEL_007", "已晋升线索的渠道不能回退处理状态");
        }
    }

    private boolean isImportedMaterial(ChannelRecordEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getMediaFileName())) {
            return false;
        }
        return ChannelType.DOCUMENT == entity.getChannelType()
                || ChannelType.AUDIO == entity.getChannelType()
                || ChannelType.VIDEO == entity.getChannelType();
    }

    private boolean requiresAiAnalysis(ChannelRecordEntity entity) {
        return isImportedMaterial(entity);
    }

    private boolean isPromotionReady(ChannelRecordEntity entity) {
        if (entity == null || entity.getLeadId() != null) {
            return false;
        }
        if (entity.getProductId() == null) {
            return false;
        }
        if (!requiresAiAnalysis(entity)) {
            return true;
        }
        return ChannelStatus.ANALYZED == entity.getStatus()
                && StringUtils.hasText(entity.getRemark())
                && entity.getAgentRunId() != null
                && entity.getAiAnalyzedAt() != null;
    }

    private String resolvePromotionBlockReason(ChannelRecordEntity entity) {
        if (entity != null && entity.getProductId() == null) {
            return "渠道数据未关联产品，请先在渠道场景配置中选择产品";
        }
        if (entity != null && ChannelStatus.WAITING_TRANSCRIPTION == entity.getStatus()) {
            return "请先完成渠道材料的中文转译";
        }
        return "请先使用渠道智能体完成AI整理并生成备注";
    }

    private void validateExternalSyncRequest(ExternalChannelSyncRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getExternalProvider())
                || !StringUtils.hasText(request.getExternalKey())
                || !StringUtils.hasText(request.getTitle())
                || !StringUtils.hasText(request.getExternalVersion())) {
            throw new BusinessException("CHANNEL_EXTERNAL_001", "外部渠道同步参数不完整");
        }
    }

    private void applyExternalChannel(ChannelRecordEntity entity, ExternalChannelSyncRequest request) {
        entity.setTitle(trimToNull(request.getTitle()));
        entity.setChannelType(ChannelType.WECOM);
        entity.setSource(normalizeSource(request.getSource()));
        entity.setContactName(firstText(request.getContactName(), entity.getContactName()));
        entity.setCompanyName(firstText(request.getCompanyName(), entity.getCompanyName()));
        entity.setPhone(firstText(request.getPhone(), entity.getPhone()));
        entity.setEmail(firstText(request.getEmail(), entity.getEmail()));
        if (request.getProductId() != null) {
            entity.setProductId(request.getProductId());
        }
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setExternalVersion(trimToNull(request.getExternalVersion()));
        entity.setSourceSnapshot(trimToNull(request.getSourceSnapshot()));
    }

    private boolean hasExternalChannelChanges(
            ChannelRecordEntity entity, ExternalChannelSyncRequest request) {
        if (!Objects.equals(
                trimToNull(entity.getExternalVersion()), trimToNull(request.getExternalVersion()))) {
            return true;
        }
        if (!Objects.equals(entity.getProductId(), request.getProductId())) {
            return true;
        }
        if (hasChangedValue(entity.getContactName(), request.getContactName())
                || hasChangedValue(entity.getCompanyName(), request.getCompanyName())
                || hasChangedValue(entity.getPhone(), request.getPhone())
                || hasChangedValue(entity.getEmail(), request.getEmail())) {
            return true;
        }
        return !Objects.equals(trimToNull(entity.getRemark()), trimToNull(request.getRemark()));
    }

    private boolean hasChangedValue(String currentValue, String incomingValue) {
        String incoming = trimToNull(incomingValue);
        return incoming != null && !Objects.equals(trimToNull(currentValue), incoming);
    }

    private String normalizePhone(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        String normalized = text.replaceAll("[^0-9]", "");
        return normalized.length() == 0 ? null : normalized;
    }

    private String normalizeCompanyName(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private void resetExternalAnalysis(ChannelRecordEntity entity) {
        entity.setAiSummary(null);
        entity.setUsefulInfo(null);
        entity.setAiAnalysisJson(null);
        entity.setAgentRunId(null);
        entity.setAiAnalyzedAt(null);
        entity.setRemark(null);
    }

    private ChannelDocumentProfile extractDocumentProfile(String documentText) {
        String text = normalizeText(documentText);
        ChannelDocumentProfile profile = new ChannelDocumentProfile();
        profile.setTitle(extractTitle(text));
        profile.setContactName(extractContactName(text));
        profile.setCompanyName(extractCompanyName(text));
        profile.setPhone(extractRegexGroup(text, "(?<!\\d)1[3-9]\\d{9}(?!\\d)", 0));
        profile.setEmail(extractRegexGroup(text, "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", 0));
        return profile;
    }

    private String extractTitle(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String title = trimToNull(line);
            if (title != null && title.length() >= 2 && title.length() <= 80) {
                return title;
            }
        }
        return null;
    }

    private String extractContactName(String text) {
        String name = extractRegexGroup(text, "用户电话沟通分析\\s*·\\s*([^\\s·，。]+)", 1);
        if (StringUtils.hasText(name)) {
            return cleanPersonName(name);
        }
        name = extractRegexGroup(text, "(联系人|客户|用户|姓名)[:：\\s]+([^\\s，。；;]+)", 2);
        if (StringUtils.hasText(name)) {
            return cleanPersonName(name);
        }
        return null;
    }

    private String extractCompanyName(String text) {
        String companyName = extractRegexGroup(text, "(公司名称|公司|企业名称|企业)[:：\\s]+([^，。；;\\s]{2,80})", 2);
        if (!StringUtils.hasText(companyName)) {
            return null;
        }
        if (companyName.contains("下级") || companyName.contains("集团型")) {
            return null;
        }
        return companyName.trim();
    }

    private String extractRegexGroup(String text, String regex, int groupIndex) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        if (groupIndex > matcher.groupCount()) {
            return null;
        }
        return trimToNull(matcher.group(groupIndex));
    }

    private String cleanPersonName(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.replace("先生", "").replace("女士", "").replace("总", "").trim();
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.replace('\u00A0', ' ').replace("\r\n", "\n").replace('\r', '\n');
        text = text.replaceAll("[ \\t\\x0B\\f]+", " ");
        return text.replaceAll("\\n\\s*\\n+", "\n").trim();
    }

    private String firstText(String first, String second) {
        String value = trimToNull(first);
        return value == null ? trimToNull(second) : value;
    }

    private String firstText(String first, String second, String third) {
        String value = firstText(first, second);
        return value == null ? trimToNull(third) : value;
    }

    private String limitText(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String resolveMediaTitle(ChannelMediaImportRequest request) {
        if (StringUtils.hasText(request.getTitle())) {
            return request.getTitle();
        }
        return request.getMediaFileName();
    }

    private ChannelType resolveMediaType(ChannelMediaImportRequest request) {
        if (request.getChannelType() != null && ChannelType.MANUAL != request.getChannelType()) {
            return request.getChannelType();
        }
        if (StringUtils.hasText(request.getMediaContentType())
                && request.getMediaContentType().toLowerCase().startsWith("video/")) {
            return ChannelType.VIDEO;
        }
        return ChannelType.AUDIO;
    }

    private String resolveLeadName(ChannelRecordEntity entity) {
        if (StringUtils.hasText(entity.getContactName())) {
            return entity.getContactName();
        }
        return entity.getTitle();
    }

    private String resolveLeadSource(ChannelRecordEntity entity) {
        String source = entity.getSource();
        if (!StringUtils.hasText(source)) {
            source = entity.getChannelType().name();
        }
        return "渠道管理-" + source;
    }

    private String resolveLeadRemark(ChannelRecordEntity entity) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "渠道标题", entity.getTitle());
        appendMarkdown(builder, entity.getRemark());
        appendLine(builder, "渠道材料", entity.getMediaFileName());
        appendLine(builder, "转译文本", entity.getTranscriptText());
        appendLine(builder, "AI总结", entity.getAiSummary());
        appendLine(builder, "有用信息", entity.getUsefulInfo());
        return builder.toString();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(label).append("：").append(value);
    }

    private void appendMarkdown(StringBuilder builder, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(value.trim());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeSource(String value) {
        return ChannelSource.from(value).name();
    }

    private String likeKeyword(String value) {
        String keyword = trimToNull(value);
        return keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }

    private ChannelResponse toResponse(ChannelRecordEntity entity) {
        ChannelResponse response = new ChannelResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setTitle(entity.getTitle());
        response.setChannelType(entity.getChannelType());
        response.setStatus(entity.getStatus());
        response.setSource(entity.getSource());
        response.setContactName(entity.getContactName());
        response.setCompanyName(entity.getCompanyName());
        response.setPhone(entity.getPhone());
        response.setEmail(entity.getEmail());
        response.setMediaFileName(entity.getMediaFileName());
        response.setMediaContentType(entity.getMediaContentType());
        response.setMediaSize(entity.getMediaSize());
        response.setMediaStorageKey(entity.getMediaStorageKey());
        response.setTranscriptText(entity.getTranscriptText());
        response.setAiSummary(entity.getAiSummary());
        response.setUsefulInfo(entity.getUsefulInfo());
        response.setAiAnalysisJson(entity.getAiAnalysisJson());
        response.setAgentRunId(entity.getAgentRunId());
        response.setAiAnalyzedAt(entity.getAiAnalyzedAt());
        response.setLeadId(entity.getLeadId());
        response.setOwnerId(entity.getOwnerId());
        response.setProductId(entity.getProductId());
        response.setRemark(entity.getRemark());
        response.setExternalProvider(entity.getExternalProvider());
        response.setExternalKey(entity.getExternalKey());
        response.setExternalVersion(entity.getExternalVersion());
        response.setSourceSnapshot(entity.getSourceSnapshot());
        response.setPromotionReady(isPromotionReady(entity));
        response.setPromotionBlockReason(
                response.isPromotionReady() || entity.getLeadId() != null
                        ? null
                        : resolvePromotionBlockReason(entity));
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private void fillOwnerName(Long tenantId, ChannelResponse response) {
        List<ChannelResponse> records = new ArrayList<ChannelResponse>();
        records.add(response);
        fillOwnerNames(tenantId, records);
        fillProductNames(tenantId, records);
    }

    private void fillOwnerNames(Long tenantId, List<ChannelResponse> records) {
        if (userNameResolver == null || records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ownerIds = new HashSet<Long>();
        for (ChannelResponse response : records) {
            if (response.getOwnerId() != null) {
                ownerIds.add(response.getOwnerId());
            }
        }
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userNameResolver.resolve(tenantId, ownerIds);
        for (ChannelResponse response : records) {
            if (response.getOwnerId() != null) {
                response.setOwnerName(names.get(response.getOwnerId()));
            }
        }
    }

    private void fillProductNames(Long tenantId, List<ChannelResponse> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> productIds = new HashSet<Long>();
        for (ChannelResponse response : records) {
            if (response.getProductId() != null) {
                productIds.add(response.getProductId());
            }
        }
        Map<Long, String> names = productReferenceResolver.resolveNames(tenantId, productIds);
        for (ChannelResponse response : records) {
            if (response.getProductId() != null) {
                response.setProductName(names.get(response.getProductId()));
            }
        }
    }

    private static class ChannelDocumentProfile {

        private String title;

        private String contactName;

        private String companyName;

        private String phone;

        private String email;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContactName() {
            return contactName;
        }

        public void setContactName(String contactName) {
            this.contactName = contactName;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

    }
}
