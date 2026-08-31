package com.hz.crm.application.channel;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.channel.dto.ChannelSourceQuery;
import com.hz.crm.application.channel.dto.ChannelSourceResponse;
import com.hz.crm.application.channel.dto.ChannelSourceSaveRequest;
import com.hz.crm.application.channel.dto.ChannelSourceSyncResult;
import com.hz.crm.application.channel.dto.ChannelSyncLogResponse;
import com.hz.crm.application.product.ProductReferenceResolver;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.ChannelSourceEntity;
import com.hz.crm.domain.channel.ChannelSourceKind;
import com.hz.crm.domain.channel.ChannelSourceStatus;
import com.hz.crm.domain.channel.ChannelSyncLogEntity;
import com.hz.crm.domain.channel.ChannelSyncMode;
import com.hz.crm.domain.channel.ChannelSyncStatus;
import com.hz.crm.domain.channel.ChannelSyncTrigger;
import com.hz.crm.domain.channel.mapper.ChannelRecordMapper;
import com.hz.crm.domain.channel.mapper.ChannelSourceMapper;
import com.hz.crm.domain.channel.mapper.ChannelSyncLogMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
public class ChannelSourceApplicationService {

    private static final String PROVIDER_WECOM_SMART_SHEET = "WECOM_SMART_SHEET";

    private static final String PROVIDER_WECOM_SMART_SHEET_EXPORT = "WECOM_SMART_SHEET_EXPORT";

    @Autowired
    private ChannelSourceMapper channelSourceMapper;

    @Autowired
    private ChannelSyncLogMapper channelSyncLogMapper;

    @Autowired
    private ChannelRecordMapper channelRecordMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired(required = false)
    private UserNameResolver userNameResolver;

    @Autowired
    private ProductReferenceResolver productReferenceResolver;

    @Transactional(readOnly = true)
    public List<ChannelSourceResponse> list(Long tenantId, ChannelSourceQuery query) {
        QueryWrapper<ChannelSourceEntity> wrapper = buildSourceWrapper(tenantId, query);
        wrapper.orderByAsc("status").orderByDesc("updated_at").orderByDesc("created_at");
        List<ChannelSourceEntity> entities = channelSourceMapper.selectList(wrapper);
        List<ChannelSourceResponse> responses = new ArrayList<ChannelSourceResponse>();
        for (ChannelSourceEntity entity : entities) {
            responses.add(toResponse(refreshStatistics(entity)));
        }
        fillOwnerNames(tenantId, responses);
        return responses;
    }

    @Transactional(readOnly = true)
    public ChannelSourceResponse detail(Long tenantId, Long id) {
        ChannelSourceEntity entity = findOne(tenantId, id);
        ChannelSourceResponse response = toResponse(refreshStatistics(entity));
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public ChannelSourceResponse resolveBySourceUrl(Long tenantId, String sourceUrl) {
        ParsedSmartSheetLink parsed = parseSmartSheetLink(sourceUrl);
        QueryWrapper<ChannelSourceEntity> wrapper = new QueryWrapper<ChannelSourceEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.eq("doc_id", parsed.getDocId());
        wrapper.eq("sheet_id", parsed.getSheetId());
        wrapper.last("limit 1");
        ChannelSourceEntity entity = channelSourceMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException(
                    "CHANNEL_SOURCE_IMPORT_002", "当前智能表格尚未在渠道来源中完成配置");
        }
        ChannelSourceResponse response = toResponse(refreshStatistics(entity));
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelSourceResponse save(Long tenantId, Long operatorId, ChannelSourceSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getSourceUrl())) {
            throw new BusinessException("CHANNEL_SOURCE_001", "渠道来源链接不能为空");
        }
        ParsedSmartSheetLink parsed = parseSmartSheetLink(request.getSourceUrl());
        LocalDateTime now = DateTimes.now();
        String externalKey = buildExternalKey(parsed.getDocId(), parsed.getSheetId());
        ChannelSourceEntity entity = request.getId() == null
                ? findByExternalKey(tenantId, PROVIDER_WECOM_SMART_SHEET, externalKey)
                : findOne(tenantId, request.getId());
        productReferenceResolver.requireSelectable(
                tenantId, request.getProductId(), entity == null ? null : entity.getProductId());
        if (entity == null) {
            entity = new ChannelSourceEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setDeleted(false);
            entity.setCreatedAt(now);
            entity.setStatus(ChannelSourceStatus.ACTIVE);
        }
        Long previousProductId = entity.getProductId();
        entity.setUpdatedAt(now);
        entity.setDeleted(false);
        entity.setName(resolveSourceName(request.getName(), parsed));
        entity.setSourceType(request.getSourceType() == null
                ? ChannelSourceKind.WECOM_SMART_SHEET
                : request.getSourceType());
        entity.setStatus(request.getStatus() == null ? ChannelSourceStatus.ACTIVE : request.getStatus());
        entity.setSyncMode(request.getSyncMode() == null ? ChannelSyncMode.SCHEDULED : request.getSyncMode());
        entity.setSourceUrl(request.getSourceUrl().trim());
        entity.setExternalProvider(PROVIDER_WECOM_SMART_SHEET);
        entity.setExternalKey(externalKey);
        entity.setWecomConfigId(request.getWecomConfigId());
        entity.setProductId(request.getProductId());
        entity.setDocId(parsed.getDocId());
        entity.setSheetId(parsed.getSheetId());
        entity.setViewId(parsed.getViewId());
        entity.setFieldMappingJson(trimToNull(request.getFieldMappingJson()));
        entity.setSyncIntervalMinutes(normalizeInterval(request.getSyncIntervalMinutes()));
        entity.setAutoSync(request.isAutoSync());
        entity.setAutoAnalyze(request.isAutoAnalyze());
        entity.setOwnerId(request.getOwnerId() == null ? operatorId : request.getOwnerId());
        if (channelSourceMapper.selectById(entity.getId()) == null) {
            channelSourceMapper.insert(entity);
        } else {
            channelSourceMapper.updateById(entity);
        }
        if (!Objects.equals(previousProductId, entity.getProductId())) {
            updatePendingRecordProduct(entity);
        }
        ChannelSourceResponse response = toResponse(refreshStatistics(entity));
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        ChannelSourceEntity entity = findOne(tenantId, id);
        entity.setDeleted(true);
        entity.setUpdatedAt(DateTimes.now());
        channelSourceMapper.updateById(entity);
    }

    @Transactional
    public ChannelSyncLogEntity startLog(Long tenantId, Long sourceId, ChannelSyncTrigger triggerType) {
        ChannelSourceEntity source = findOne(tenantId, sourceId);
        LocalDateTime now = DateTimes.now();
        ChannelSyncLogEntity log = new ChannelSyncLogEntity();
        log.setId(snowflakeIdGenerator.nextId());
        log.setTenantId(tenantId);
        log.setDeleted(false);
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        log.setSourceId(source.getId());
        log.setTriggerType(triggerType == null ? ChannelSyncTrigger.MANUAL : triggerType);
        log.setStatus(ChannelSyncStatus.RUNNING);
        log.setStartedAt(now);
        channelSyncLogMapper.insert(log);
        source.setLastSyncAt(now);
        source.setLastError(null);
        source.setUpdatedAt(now);
        channelSourceMapper.updateById(source);
        return log;
    }

    @Transactional
    public ChannelSourceSyncResult completeLog(
            Long tenantId,
            Long sourceId,
            Long logId,
            ChannelSourceSyncResult result,
            String fieldSnapshot) {
        LocalDateTime now = DateTimes.now();
        ChannelSyncLogEntity log = findLog(tenantId, logId);
        log.setStatus(ChannelSyncStatus.SUCCESS);
        log.setFinishedAt(now);
        log.setFetchedCount(Integer.valueOf(result.getFetchedCount()));
        log.setCreatedCount(Integer.valueOf(result.getCreatedCount()));
        log.setUpdatedCount(Integer.valueOf(result.getUpdatedCount()));
        log.setSkippedCount(Integer.valueOf(result.getSkippedCount()));
        log.setFailedCount(Integer.valueOf(result.getFailedCount()));
        log.setUpdatedAt(now);
        channelSyncLogMapper.updateById(log);
        ChannelSourceEntity source = findOne(tenantId, sourceId);
        source.setStatus(ChannelSourceStatus.ACTIVE);
        source.setLastSuccessAt(now);
        source.setLastError(null);
        source.setLatestFieldSnapshot(trimToNull(fieldSnapshot));
        source.setDuplicateCount(Long.valueOf(safeLong(source.getDuplicateCount()) + result.getSkippedCount()));
        source.setFailedCount(Long.valueOf(safeLong(source.getFailedCount()) + result.getFailedCount()));
        source.setUpdatedAt(now);
        channelSourceMapper.updateById(refreshStatistics(source));
        result.setSourceId(sourceId);
        result.setLogId(logId);
        result.setMessage("同步完成");
        return result;
    }

    @Transactional
    public ChannelSourceSyncResult failLog(Long tenantId, Long sourceId, Long logId, String message) {
        LocalDateTime now = DateTimes.now();
        ChannelSyncLogEntity log = findLog(tenantId, logId);
        log.setStatus(ChannelSyncStatus.FAILED);
        log.setFinishedAt(now);
        log.setErrorMessage(limitText(message, 2000));
        log.setUpdatedAt(now);
        channelSyncLogMapper.updateById(log);
        ChannelSourceEntity source = findOne(tenantId, sourceId);
        source.setStatus(ChannelSourceStatus.ERROR);
        source.setLastError(limitText(message, 2000));
        source.setFailedCount(Long.valueOf(safeLong(source.getFailedCount()) + 1L));
        source.setUpdatedAt(now);
        channelSourceMapper.updateById(source);
        ChannelSourceSyncResult result = new ChannelSourceSyncResult();
        result.setSourceId(sourceId);
        result.setLogId(logId);
        result.setFailedCount(1);
        result.setMessage(message);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ChannelSyncLogResponse> latestLogs(Long tenantId, Long sourceId, int limit) {
        findOne(tenantId, sourceId);
        QueryWrapper<ChannelSyncLogEntity> wrapper = new QueryWrapper<ChannelSyncLogEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.eq("source_id", sourceId);
        wrapper.orderByDesc("created_at");
        wrapper.last("limit " + Math.max(1, Math.min(limit, 20)));
        List<ChannelSyncLogEntity> logs = channelSyncLogMapper.selectList(wrapper);
        List<ChannelSyncLogResponse> responses = new ArrayList<ChannelSyncLogResponse>();
        for (ChannelSyncLogEntity log : logs) {
            responses.add(toLogResponse(log));
        }
        return responses;
    }

    private QueryWrapper<ChannelSourceEntity> buildSourceWrapper(Long tenantId, ChannelSourceQuery query) {
        ChannelSourceQuery safeQuery = query == null ? new ChannelSourceQuery() : query;
        QueryWrapper<ChannelSourceEntity> wrapper = new QueryWrapper<ChannelSourceEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        if (safeQuery.getSourceType() != null) {
            wrapper.eq("source_type", safeQuery.getSourceType().name());
        }
        if (safeQuery.getStatus() != null) {
            wrapper.eq("status", safeQuery.getStatus().name());
        }
        String keyword = trimToNull(safeQuery.getKeyword());
        if (keyword != null) {
            String like = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
            wrapper.and(value -> value
                    .apply("lower(coalesce(name, '')) like {0}", like)
                    .or()
                    .apply("lower(coalesce(source_url, '')) like {0}", like)
                    .or()
                    .apply("lower(coalesce(doc_id, '')) like {0}", like)
                    .or()
                    .apply("lower(coalesce(sheet_id, '')) like {0}", like));
        }
        return wrapper;
    }

    private ChannelSourceEntity refreshStatistics(ChannelSourceEntity source) {
        if (source == null || source.getId() == null || !StringUtils.hasText(source.getExternalKey())) {
            return source;
        }
        QueryWrapper<ChannelRecordEntity> totalWrapper = sourceRecordWrapper(source);
        source.setTotalRecordCount(channelRecordMapper.selectCount(totalWrapper));
        QueryWrapper<ChannelRecordEntity> todayWrapper = sourceRecordWrapper(source);
        LocalDateTime dayStart = DateTimes.now().toLocalDate().atStartOfDay();
        todayWrapper.ge("created_at", dayStart);
        todayWrapper.lt("created_at", dayStart.plusDays(1));
        source.setTodayNewCount(channelRecordMapper.selectCount(todayWrapper));
        QueryWrapper<ChannelRecordEntity> convertedWrapper = sourceRecordWrapper(source);
        convertedWrapper.isNotNull("lead_id");
        source.setConvertedLeadCount(channelRecordMapper.selectCount(convertedWrapper));
        return source;
    }

    private QueryWrapper<ChannelRecordEntity> sourceRecordWrapper(ChannelSourceEntity source) {
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", source.getTenantId());
        wrapper.eq("deleted", false);
        wrapper.and(condition -> condition
                .nested(direct -> direct
                        .eq("external_provider", PROVIDER_WECOM_SMART_SHEET)
                        .likeRight("external_key", source.getExternalKey() + ":"))
                .or(exported -> exported
                        .eq("external_provider", PROVIDER_WECOM_SMART_SHEET_EXPORT)
                        .likeRight("external_key", source.getId() + ":")));
        return wrapper;
    }

    private void updatePendingRecordProduct(ChannelSourceEntity source) {
        QueryWrapper<ChannelRecordEntity> wrapper = sourceRecordWrapper(source);
        wrapper.isNull("lead_id");
        ChannelRecordEntity update = new ChannelRecordEntity();
        update.setProductId(source.getProductId());
        update.setUpdatedAt(DateTimes.now());
        channelRecordMapper.update(update, wrapper);
    }

    private ParsedSmartSheetLink parseSmartSheetLink(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl.trim());
            String path = uri.getPath();
            String[] parts = path == null ? new String[0] : path.split("/");
            String docId = null;
            for (int index = 0; index < parts.length; index++) {
                if ("smartsheet".equals(parts[index]) && index + 1 < parts.length) {
                    docId = parts[index + 1];
                    break;
                }
            }
            Map<String, String> query = parseQuery(uri.getRawQuery());
            String sheetId = query.get("tab");
            if (!StringUtils.hasText(docId) || !StringUtils.hasText(sheetId)) {
                throw new BusinessException("CHANNEL_SOURCE_002", "企微智能表格链接缺少docid或tab");
            }
            ParsedSmartSheetLink parsed = new ParsedSmartSheetLink();
            parsed.setDocId(docId.trim());
            parsed.setSheetId(sheetId.trim());
            parsed.setViewId(trimToNull(query.get("viewId")));
            return parsed;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("CHANNEL_SOURCE_003", "企微智能表格链接解析失败");
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new HashMap<String, String>();
        if (!StringUtils.hasText(rawQuery)) {
            return query;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = decode(pair.substring(0, index));
            String value = decode(pair.substring(index + 1));
            query.put(key, value);
        }
        return query;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception ex) {
            return value;
        }
    }

    private String buildExternalKey(String docId, String sheetId) {
        return docId + ":" + sheetId;
    }

    private String resolveSourceName(String name, ParsedSmartSheetLink parsed) {
        String text = trimToNull(name);
        if (text != null) {
            return text;
        }
        return "企微智能表格-" + parsed.getSheetId();
    }

    private int normalizeInterval(Integer value) {
        if (value == null) {
            return 10;
        }
        return Math.max(1, Math.min(value.intValue(), 1440));
    }

    private ChannelSourceEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("CHANNEL_SOURCE_004", "渠道来源编号不能为空");
        }
        QueryWrapper<ChannelSourceEntity> wrapper = new QueryWrapper<ChannelSourceEntity>();
        wrapper.eq("id", id);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        ChannelSourceEntity entity = channelSourceMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("CHANNEL_SOURCE_005", "渠道来源不存在");
        }
        return entity;
    }

    private ChannelSourceEntity findByExternalKey(Long tenantId, String provider, String externalKey) {
        QueryWrapper<ChannelSourceEntity> wrapper = new QueryWrapper<ChannelSourceEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("external_provider", provider);
        wrapper.eq("external_key", externalKey);
        wrapper.last("limit 1");
        return channelSourceMapper.selectOne(wrapper);
    }

    private ChannelSyncLogEntity findLog(Long tenantId, Long logId) {
        if (logId == null) {
            throw new BusinessException("CHANNEL_SYNC_LOG_001", "同步日志编号不能为空");
        }
        QueryWrapper<ChannelSyncLogEntity> wrapper = new QueryWrapper<ChannelSyncLogEntity>();
        wrapper.eq("id", logId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        ChannelSyncLogEntity entity = channelSyncLogMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("CHANNEL_SYNC_LOG_002", "同步日志不存在");
        }
        return entity;
    }

    private ChannelSourceResponse toResponse(ChannelSourceEntity entity) {
        ChannelSourceResponse response = new ChannelSourceResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setName(entity.getName());
        response.setSourceType(entity.getSourceType());
        response.setStatus(entity.getStatus());
        response.setSyncMode(entity.getSyncMode());
        response.setSourceUrl(entity.getSourceUrl());
        response.setExternalProvider(entity.getExternalProvider());
        response.setExternalKey(entity.getExternalKey());
        response.setWecomConfigId(entity.getWecomConfigId());
        response.setProductId(entity.getProductId());
        response.setProductName(productReferenceResolver.resolveName(
                entity.getTenantId(), entity.getProductId()));
        response.setDocId(entity.getDocId());
        response.setSheetId(entity.getSheetId());
        response.setViewId(entity.getViewId());
        response.setFieldMappingJson(entity.getFieldMappingJson());
        response.setSyncIntervalMinutes(entity.getSyncIntervalMinutes());
        response.setAutoSync(entity.isAutoSync());
        response.setAutoAnalyze(entity.isAutoAnalyze());
        response.setOwnerId(entity.getOwnerId());
        response.setLastSyncAt(entity.getLastSyncAt());
        response.setLastSuccessAt(entity.getLastSuccessAt());
        response.setLastError(entity.getLastError());
        response.setTotalRecordCount(entity.getTotalRecordCount());
        response.setTodayNewCount(entity.getTodayNewCount());
        response.setConvertedLeadCount(entity.getConvertedLeadCount());
        response.setDuplicateCount(entity.getDuplicateCount());
        response.setFailedCount(entity.getFailedCount());
        response.setLatestFieldSnapshot(entity.getLatestFieldSnapshot());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private ChannelSyncLogResponse toLogResponse(ChannelSyncLogEntity entity) {
        ChannelSyncLogResponse response = new ChannelSyncLogResponse();
        response.setId(entity.getId());
        response.setSourceId(entity.getSourceId());
        response.setTriggerType(entity.getTriggerType());
        response.setStatus(entity.getStatus());
        response.setStartedAt(entity.getStartedAt());
        response.setFinishedAt(entity.getFinishedAt());
        response.setFetchedCount(entity.getFetchedCount());
        response.setCreatedCount(entity.getCreatedCount());
        response.setUpdatedCount(entity.getUpdatedCount());
        response.setSkippedCount(entity.getSkippedCount());
        response.setFailedCount(entity.getFailedCount());
        response.setErrorMessage(entity.getErrorMessage());
        return response;
    }

    private void fillOwnerName(Long tenantId, ChannelSourceResponse response) {
        List<ChannelSourceResponse> responses = new ArrayList<ChannelSourceResponse>();
        responses.add(response);
        fillOwnerNames(tenantId, responses);
    }

    private void fillOwnerNames(Long tenantId, List<ChannelSourceResponse> responses) {
        if (userNameResolver == null || responses == null || responses.isEmpty()) {
            return;
        }
        Set<Long> ownerIds = new HashSet<Long>();
        for (ChannelSourceResponse response : responses) {
            if (response.getOwnerId() != null) {
                ownerIds.add(response.getOwnerId());
            }
        }
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userNameResolver.resolve(tenantId, ownerIds);
        for (ChannelSourceResponse response : responses) {
            response.setOwnerName(names.get(response.getOwnerId()));
        }
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value.longValue();
    }

    private String limitText(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static class ParsedSmartSheetLink {

        private String docId;

        private String sheetId;

        private String viewId;

        String getDocId() {
            return docId;
        }

        void setDocId(String docId) {
            this.docId = docId;
        }

        String getSheetId() {
            return sheetId;
        }

        void setSheetId(String sheetId) {
            this.sheetId = sheetId;
        }

        String getViewId() {
            return viewId;
        }

        void setViewId(String viewId) {
            this.viewId = viewId;
        }
    }
}
