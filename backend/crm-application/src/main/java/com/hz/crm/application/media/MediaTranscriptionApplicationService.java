package com.hz.crm.application.media;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.application.media.dto.MediaTranscriptionCreateRequest;
import com.hz.crm.application.media.dto.MediaTranscriptionResponse;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.followup.FollowupRecordEntity;
import com.hz.crm.domain.followup.mapper.FollowupRecordMapper;
import com.hz.crm.domain.media.MediaTranscriptionTaskEntity;
import com.hz.crm.domain.media.mapper.MediaTranscriptionTaskMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MediaTranscriptionApplicationService {

    public static final String BUSINESS_TYPE_FOLLOWUP = "FOLLOWUP";

    public static final String PROVIDER_VOLCENGINE = "VOLCENGINE";

    public static final String STATUS_PENDING = "PENDING";

    public static final String STATUS_EXTRACTING = "EXTRACTING";

    public static final String STATUS_READY = "READY";

    public static final String STATUS_SUBMITTED = "SUBMITTED";

    public static final String STATUS_PROCESSING = "PROCESSING";

    public static final String STATUS_SUCCESS = "SUCCESS";

    public static final String STATUS_FAILED = "FAILED";

    @Autowired
    private MediaTranscriptionTaskMapper mediaTranscriptionTaskMapper;

    @Autowired
    private FollowupRecordMapper followupRecordMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public MediaTranscriptionResponse createFollowupTask(
            Long tenantId,
            Long operatorId,
            String dataScope,
            MediaTranscriptionCreateRequest request) {
        if (request == null || request.getBusinessId() == null) {
            throw new BusinessException("MEDIA_001", "请选择要挂载的跟进记录");
        }
        if (!StringUtils.hasText(request.getStorageKey()) || !StringUtils.hasText(request.getFileUrl())) {
            throw new BusinessException("MEDIA_002", "音视频文件上传结果不完整");
        }
        FollowupRecordEntity followup = findFollowup(tenantId, request.getBusinessId());
        checkDataScope(operatorId, dataScope, followup.getOwnerId());
        LocalDateTime now = DateTimes.now();
        MediaTranscriptionTaskEntity entity = new MediaTranscriptionTaskEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(tenantId);
        entity.setDeleted(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setBusinessType(BUSINESS_TYPE_FOLLOWUP);
        entity.setBusinessId(followup.getId());
        entity.setTargetType(followup.getTargetType() == null ? null : followup.getTargetType().name());
        entity.setTargetId(followup.getTargetId());
        entity.setTargetName(followup.getTargetName());
        entity.setProvider(PROVIDER_VOLCENGINE);
        entity.setStatus(STATUS_PENDING);
        entity.setLanguage("zh-CN");
        entity.setFileName(shrink(request.getFileName(), 256));
        entity.setContentType(shrink(request.getContentType(), 128));
        entity.setFileSize(request.getFileSize());
        entity.setStorageKey(shrink(request.getStorageKey(), 512));
        entity.setFileUrl(request.getFileUrl());
        entity.setFileFormat(shrink(request.getFileFormat(), 16));
        entity.setProgress(Integer.valueOf(0));
        entity.setRetryCount(Integer.valueOf(0));
        entity.setOwnerId(followup.getOwnerId());
        entity.setCreatorId(operatorId);
        mediaTranscriptionTaskMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<MediaTranscriptionResponse> listByFollowup(
            Long tenantId,
            Long userId,
            String dataScope,
            Long followupId) {
        FollowupRecordEntity followup = findFollowup(tenantId, followupId);
        checkDataScope(userId, dataScope, followup.getOwnerId());
        return listByFollowupIds(tenantId, Collections.singletonList(followupId));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<MediaTranscriptionResponse>> groupByFollowupIds(Long tenantId, Collection<Long> followupIds) {
        Map<Long, List<MediaTranscriptionResponse>> result = new LinkedHashMap<Long, List<MediaTranscriptionResponse>>();
        if (followupIds == null || followupIds.isEmpty()) {
            return result;
        }
        LambdaQueryWrapper<MediaTranscriptionTaskEntity> wrapper = Wrappers.<MediaTranscriptionTaskEntity>lambdaQuery()
                .eq(MediaTranscriptionTaskEntity::getTenantId, tenantId)
                .eq(MediaTranscriptionTaskEntity::isDeleted, false)
                .eq(MediaTranscriptionTaskEntity::getBusinessType, BUSINESS_TYPE_FOLLOWUP)
                .in(MediaTranscriptionTaskEntity::getBusinessId, followupIds)
                .orderByDesc(MediaTranscriptionTaskEntity::getCreatedAt)
                .orderByDesc(MediaTranscriptionTaskEntity::getId);
        List<MediaTranscriptionTaskEntity> entities = mediaTranscriptionTaskMapper.selectList(wrapper);
        for (MediaTranscriptionTaskEntity entity : entities) {
            Long followupId = entity.getBusinessId();
            if (!result.containsKey(followupId)) {
                result.put(followupId, new ArrayList<MediaTranscriptionResponse>());
            }
            result.get(followupId).add(toResponse(entity));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<MediaTranscriptionResponse> listByFollowupIds(Long tenantId, Collection<Long> followupIds) {
        List<MediaTranscriptionResponse> responses = new ArrayList<MediaTranscriptionResponse>();
        Map<Long, List<MediaTranscriptionResponse>> grouped = groupByFollowupIds(tenantId, followupIds);
        for (List<MediaTranscriptionResponse> items : grouped.values()) {
            responses.addAll(items);
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<MediaTranscriptionTaskEntity> listWaitingTasks(int batchSize, int maxRetry, int retryDelaySeconds) {
        LocalDateTime now = DateTimes.now();
        LocalDateTime staleLockedAt = now.minusMinutes(30);
        LocalDateTime retryBefore = now.minusSeconds(Math.max(10, retryDelaySeconds));
        int safeBatchSize = Math.max(1, Math.min(batchSize, 50));
        return mediaTranscriptionTaskMapper.selectList(Wrappers.<MediaTranscriptionTaskEntity>lambdaQuery()
                .eq(MediaTranscriptionTaskEntity::isDeleted, false)
                .and(wrapper -> wrapper
                        .in(MediaTranscriptionTaskEntity::getStatus,
                                STATUS_PENDING,
                                STATUS_READY,
                                STATUS_SUBMITTED,
                                STATUS_PROCESSING)
                        .or(value -> value
                                .eq(MediaTranscriptionTaskEntity::getStatus, STATUS_FAILED)
                                .lt(MediaTranscriptionTaskEntity::getRetryCount, Integer.valueOf(Math.max(1, maxRetry)))
                                .le(MediaTranscriptionTaskEntity::getUpdatedAt, retryBefore)))
                .and(wrapper -> wrapper
                        .isNull(MediaTranscriptionTaskEntity::getLockedAt)
                        .or()
                        .le(MediaTranscriptionTaskEntity::getLockedAt, staleLockedAt))
                .orderByAsc(MediaTranscriptionTaskEntity::getCreatedAt)
                .last("limit " + safeBatchSize));
    }

    @Transactional
    public boolean claim(MediaTranscriptionTaskEntity task, String processorId) {
        if (task == null || task.getId() == null || task.getTenantId() == null) {
            return false;
        }
        LocalDateTime now = DateTimes.now();
        LocalDateTime staleLockedAt = now.minusMinutes(30);
        LambdaUpdateWrapper<MediaTranscriptionTaskEntity> wrapper = Wrappers.<MediaTranscriptionTaskEntity>lambdaUpdate()
                .eq(MediaTranscriptionTaskEntity::getId, task.getId())
                .eq(MediaTranscriptionTaskEntity::getTenantId, task.getTenantId())
                .eq(MediaTranscriptionTaskEntity::isDeleted, false)
                .and(value -> value
                        .isNull(MediaTranscriptionTaskEntity::getLockedAt)
                        .or()
                        .le(MediaTranscriptionTaskEntity::getLockedAt, staleLockedAt))
                .set(MediaTranscriptionTaskEntity::getLockedAt, now)
                .set(MediaTranscriptionTaskEntity::getLockedBy, processorId)
                .set(MediaTranscriptionTaskEntity::getUpdatedAt, now);
        return mediaTranscriptionTaskMapper.update(null, wrapper) == 1;
    }

    @Transactional
    public void markExtracting(MediaTranscriptionTaskEntity task) {
        updateStatus(task, STATUS_EXTRACTING, 10, null);
    }

    @Transactional
    public void markReady(MediaTranscriptionTaskEntity task, String audioFileName, String audioContentType,
            Long audioFileSize, String audioStorageKey, String audioFileUrl, String audioFileFormat) {
        LocalDateTime now = DateTimes.now();
        mediaTranscriptionTaskMapper.update(null, Wrappers.<MediaTranscriptionTaskEntity>lambdaUpdate()
                .eq(MediaTranscriptionTaskEntity::getId, task.getId())
                .eq(MediaTranscriptionTaskEntity::getTenantId, task.getTenantId())
                .set(MediaTranscriptionTaskEntity::getStatus, STATUS_READY)
                .set(MediaTranscriptionTaskEntity::getAudioFileName, shrink(audioFileName, 256))
                .set(MediaTranscriptionTaskEntity::getAudioContentType, shrink(audioContentType, 128))
                .set(MediaTranscriptionTaskEntity::getAudioFileSize, audioFileSize)
                .set(MediaTranscriptionTaskEntity::getAudioStorageKey, shrink(audioStorageKey, 512))
                .set(MediaTranscriptionTaskEntity::getAudioFileUrl, audioFileUrl)
                .set(MediaTranscriptionTaskEntity::getAudioFileFormat, shrink(audioFileFormat, 16))
                .set(MediaTranscriptionTaskEntity::getProgress, Integer.valueOf(25))
                .set(MediaTranscriptionTaskEntity::getErrorMessage, null)
                .set(MediaTranscriptionTaskEntity::getUpdatedAt, now));
    }

    @Transactional
    public void markSubmitted(MediaTranscriptionTaskEntity task, String providerTaskId, String requestId, String rawResultJson) {
        LocalDateTime now = DateTimes.now();
        mediaTranscriptionTaskMapper.update(null, Wrappers.<MediaTranscriptionTaskEntity>lambdaUpdate()
                .eq(MediaTranscriptionTaskEntity::getId, task.getId())
                .eq(MediaTranscriptionTaskEntity::getTenantId, task.getTenantId())
                .set(MediaTranscriptionTaskEntity::getStatus, STATUS_SUBMITTED)
                .set(MediaTranscriptionTaskEntity::getProviderTaskId, shrink(providerTaskId, 128))
                .set(MediaTranscriptionTaskEntity::getProviderRequestId, shrink(requestId, 128))
                .set(MediaTranscriptionTaskEntity::getRawResultJson, rawResultJson)
                .set(MediaTranscriptionTaskEntity::getSubmittedAt, now)
                .set(MediaTranscriptionTaskEntity::getProgress, Integer.valueOf(40))
                .set(MediaTranscriptionTaskEntity::getLockedAt, null)
                .set(MediaTranscriptionTaskEntity::getLockedBy, null)
                .set(MediaTranscriptionTaskEntity::getErrorMessage, null)
                .set(MediaTranscriptionTaskEntity::getUpdatedAt, now));
    }

    @Transactional
    public void markProcessing(MediaTranscriptionTaskEntity task, String rawResultJson) {
        LocalDateTime now = DateTimes.now();
        mediaTranscriptionTaskMapper.update(null, Wrappers.<MediaTranscriptionTaskEntity>lambdaUpdate()
                .eq(MediaTranscriptionTaskEntity::getId, task.getId())
                .eq(MediaTranscriptionTaskEntity::getTenantId, task.getTenantId())
                .set(MediaTranscriptionTaskEntity::getStatus, STATUS_PROCESSING)
                .set(MediaTranscriptionTaskEntity::getRawResultJson, rawResultJson)
                .set(MediaTranscriptionTaskEntity::getProgress, Integer.valueOf(65))
                .set(MediaTranscriptionTaskEntity::getLockedAt, null)
                .set(MediaTranscriptionTaskEntity::getLockedBy, null)
                .set(MediaTranscriptionTaskEntity::getUpdatedAt, now));
    }

    @Transactional
    public void markSuccess(MediaTranscriptionTaskEntity task, String transcriptText, String utterancesJson, String rawResultJson) {
        LocalDateTime now = DateTimes.now();
        mediaTranscriptionTaskMapper.update(null, Wrappers.<MediaTranscriptionTaskEntity>lambdaUpdate()
                .eq(MediaTranscriptionTaskEntity::getId, task.getId())
                .eq(MediaTranscriptionTaskEntity::getTenantId, task.getTenantId())
                .set(MediaTranscriptionTaskEntity::getStatus, STATUS_SUCCESS)
                .set(MediaTranscriptionTaskEntity::getTranscriptText, transcriptText)
                .set(MediaTranscriptionTaskEntity::getUtterancesJson, utterancesJson)
                .set(MediaTranscriptionTaskEntity::getRawResultJson, rawResultJson)
                .set(MediaTranscriptionTaskEntity::getProgress, Integer.valueOf(100))
                .set(MediaTranscriptionTaskEntity::getFinishedAt, now)
                .set(MediaTranscriptionTaskEntity::getLockedAt, null)
                .set(MediaTranscriptionTaskEntity::getLockedBy, null)
                .set(MediaTranscriptionTaskEntity::getErrorMessage, null)
                .set(MediaTranscriptionTaskEntity::getUpdatedAt, now));
    }

    @Transactional
    public void markFailed(MediaTranscriptionTaskEntity task, String errorMessage) {
        LocalDateTime now = DateTimes.now();
        int retryCount = task.getRetryCount() == null ? 1 : task.getRetryCount().intValue() + 1;
        mediaTranscriptionTaskMapper.update(null, Wrappers.<MediaTranscriptionTaskEntity>lambdaUpdate()
                .eq(MediaTranscriptionTaskEntity::getId, task.getId())
                .eq(MediaTranscriptionTaskEntity::getTenantId, task.getTenantId())
                .set(MediaTranscriptionTaskEntity::getStatus, STATUS_FAILED)
                .set(MediaTranscriptionTaskEntity::getRetryCount, Integer.valueOf(retryCount))
                .set(MediaTranscriptionTaskEntity::getErrorMessage, shrink(errorMessage, 2000))
                .set(MediaTranscriptionTaskEntity::getLockedAt, null)
                .set(MediaTranscriptionTaskEntity::getLockedBy, null)
                .set(MediaTranscriptionTaskEntity::getUpdatedAt, now));
    }

    @Transactional
    public void releaseLock(MediaTranscriptionTaskEntity task) {
        mediaTranscriptionTaskMapper.update(null, Wrappers.<MediaTranscriptionTaskEntity>lambdaUpdate()
                .eq(MediaTranscriptionTaskEntity::getId, task.getId())
                .eq(MediaTranscriptionTaskEntity::getTenantId, task.getTenantId())
                .set(MediaTranscriptionTaskEntity::getLockedAt, null)
                .set(MediaTranscriptionTaskEntity::getLockedBy, null)
                .set(MediaTranscriptionTaskEntity::getUpdatedAt, DateTimes.now()));
    }

    private void updateStatus(MediaTranscriptionTaskEntity task, String status, int progress, String errorMessage) {
        mediaTranscriptionTaskMapper.update(null, Wrappers.<MediaTranscriptionTaskEntity>lambdaUpdate()
                .eq(MediaTranscriptionTaskEntity::getId, task.getId())
                .eq(MediaTranscriptionTaskEntity::getTenantId, task.getTenantId())
                .set(MediaTranscriptionTaskEntity::getStatus, status)
                .set(MediaTranscriptionTaskEntity::getProgress, Integer.valueOf(progress))
                .set(MediaTranscriptionTaskEntity::getErrorMessage, shrink(errorMessage, 2000))
                .set(MediaTranscriptionTaskEntity::getUpdatedAt, DateTimes.now()));
    }

    private FollowupRecordEntity findFollowup(Long tenantId, Long followupId) {
        if (tenantId == null || followupId == null) {
            throw new BusinessException("MEDIA_003", "跟进记录编号不能为空");
        }
        FollowupRecordEntity entity = followupRecordMapper.selectOne(Wrappers.<FollowupRecordEntity>lambdaQuery()
                .eq(FollowupRecordEntity::getId, followupId)
                .eq(FollowupRecordEntity::getTenantId, tenantId)
                .eq(FollowupRecordEntity::isDeleted, false));
        if (entity == null) {
            throw new BusinessException("MEDIA_004", "跟进记录不存在");
        }
        return entity;
    }

    private void checkDataScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_001", "无权访问该数据");
        }
    }

    public MediaTranscriptionResponse toResponse(MediaTranscriptionTaskEntity entity) {
        MediaTranscriptionResponse response = new MediaTranscriptionResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setBusinessType(entity.getBusinessType());
        response.setBusinessId(entity.getBusinessId());
        response.setTargetType(entity.getTargetType());
        response.setTargetId(entity.getTargetId());
        response.setTargetName(entity.getTargetName());
        response.setProvider(entity.getProvider());
        response.setStatus(entity.getStatus());
        response.setProviderTaskId(entity.getProviderTaskId());
        response.setLanguage(entity.getLanguage());
        response.setFileName(entity.getFileName());
        response.setContentType(entity.getContentType());
        response.setFileSize(entity.getFileSize());
        response.setFileUrl(entity.getFileUrl());
        response.setFileFormat(entity.getFileFormat());
        response.setAudioFileName(entity.getAudioFileName());
        response.setAudioFileUrl(entity.getAudioFileUrl());
        response.setAudioFileFormat(entity.getAudioFileFormat());
        response.setProgress(entity.getProgress());
        response.setErrorMessage(entity.getErrorMessage());
        response.setTranscriptText(entity.getTranscriptText());
        response.setSubmittedAt(entity.getSubmittedAt());
        response.setFinishedAt(entity.getFinishedAt());
        response.setOwnerId(entity.getOwnerId());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private String shrink(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
