package com.hz.crm.application.channel;

import com.hz.crm.application.channel.dto.ChannelMediaImportRequest;
import com.hz.crm.application.channel.dto.ChannelPromoteRequest;
import com.hz.crm.application.channel.dto.ChannelQuery;
import com.hz.crm.application.channel.dto.ChannelResponse;
import com.hz.crm.application.channel.dto.ChannelSaveRequest;
import com.hz.crm.application.lead.LeadApplicationService;
import com.hz.crm.application.lead.dto.LeadResponse;
import com.hz.crm.application.lead.dto.LeadSaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.ChannelSource;
import com.hz.crm.domain.channel.ChannelStatus;
import com.hz.crm.domain.channel.ChannelType;
import com.hz.crm.domain.channel.repository.ChannelRecordRepository;
import com.hz.crm.domain.lead.LeadStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChannelApplicationService {

    @Autowired
    private ChannelRecordRepository channelRecordRepository;

    @Autowired
    private LeadApplicationService leadApplicationService;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired(required = false)
    private UserNameResolver userNameResolver;

    @Transactional(readOnly = true)
    public PageData<ChannelResponse> page(Long tenantId, Long userId, String dataScope, ChannelQuery query) {
        ChannelQuery safeQuery = query == null ? new ChannelQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Long ownerId = "SELF".equals(dataScope) ? userId : null;
        Page<ChannelRecordEntity> page = channelRecordRepository.search(
                tenantId,
                ownerId,
                likeKeyword(safeQuery.getKeyword()),
                safeQuery.getStatus(),
                safeQuery.getChannelType(),
                pageRequest);
        List<ChannelResponse> records = new ArrayList<ChannelResponse>();
        for (ChannelRecordEntity entity : page.getContent()) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        return PageData.of(page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), records);
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
        if (request.getId() == null) {
            entity = new ChannelRecordEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setOwnerId(operatorId);
            entity.setStatus(ChannelStatus.NEW);
        } else {
            entity = findOne(tenantId, request.getId());
            checkDataScope(operatorId, dataScope, entity.getOwnerId());
        }
        entity.setTitle(trimToNull(request.getTitle()));
        entity.setChannelType(request.getChannelType() == null ? ChannelType.MANUAL : request.getChannelType());
        entity.setSource(normalizeSource(request.getSource()));
        entity.setContactName(trimToNull(request.getContactName()));
        entity.setCompanyName(trimToNull(request.getCompanyName()));
        entity.setPhone(trimToNull(request.getPhone()));
        entity.setEmail(trimToNull(request.getEmail()));
        entity.setRemark(trimToNull(request.getRemark()));
        ChannelResponse response = toResponse(channelRecordRepository.save(entity));
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        channelRecordRepository.save(entity);
    }

    @Transactional
    public ChannelResponse importMedia(Long tenantId, Long operatorId, ChannelMediaImportRequest request) {
        if (request == null || !StringUtils.hasText(request.getMediaFileName())) {
            throw new BusinessException("CHANNEL_002", "请上传录音或视频文件");
        }
        ChannelRecordEntity entity = new ChannelRecordEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(tenantId);
        entity.setOwnerId(operatorId);
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
        entity.setRemark(trimToNull(request.getRemark()));
        ChannelResponse response = toResponse(channelRecordRepository.save(entity));
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse prepareTranscription(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        checkNotPromoted(entity);
        entity.setStatus(ChannelStatus.WAITING_TRANSCRIPTION);
        ChannelResponse response = toResponse(channelRecordRepository.save(entity));
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse prepareAiAnalysis(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        checkNotPromoted(entity);
        entity.setStatus(ChannelStatus.WAITING_AI_ANALYSIS);
        ChannelResponse response = toResponse(channelRecordRepository.save(entity));
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
        LeadSaveRequest leadRequest = new LeadSaveRequest();
        leadRequest.setName(resolveLeadName(entity));
        leadRequest.setCompanyName(entity.getCompanyName());
        leadRequest.setPhone(entity.getPhone());
        leadRequest.setEmail(entity.getEmail());
        leadRequest.setSource(resolveLeadSource(entity));
        leadRequest.setStatus(LeadStatus.recommended());
        leadRequest.setOwnerId(request.getOwnerId() == null ? entity.getOwnerId() : request.getOwnerId());
        leadRequest.setRemark(resolveLeadRemark(entity));
        LeadResponse lead = leadApplicationService.save(tenantId, operatorId, dataScope, leadRequest);
        entity.setLeadId(lead.getId());
        entity.setStatus(ChannelStatus.PROMOTED);
        ChannelResponse response = toResponse(channelRecordRepository.save(entity));
        fillOwnerName(tenantId, response);
        return response;
    }

    private ChannelRecordEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("CHANNEL_003", "渠道编号不能为空");
        }
        return channelRecordRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("CHANNEL_005", "渠道记录不存在"));
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
        appendLine(builder, "渠道备注", entity.getRemark());
        appendLine(builder, "音视频文件", entity.getMediaFileName());
        appendLine(builder, "转译文本", entity.getTranscriptText());
        appendLine(builder, "AI总结", entity.getAiSummary());
        appendLine(builder, "有用信息", entity.getUsefulInfo());
        String remark = builder.toString();
        if (remark.length() > 512) {
            return remark.substring(0, 512);
        }
        return remark;
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
        response.setLeadId(entity.getLeadId());
        response.setOwnerId(entity.getOwnerId());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private void fillOwnerName(Long tenantId, ChannelResponse response) {
        List<ChannelResponse> records = new ArrayList<ChannelResponse>();
        records.add(response);
        fillOwnerNames(tenantId, records);
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
}
