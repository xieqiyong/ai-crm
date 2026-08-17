package com.hz.crm.application.system;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.system.dto.FollowupTaskSettingsResponse;
import com.hz.crm.application.system.dto.FollowupTaskSettingsSaveRequest;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.system.SystemParameterEntity;
import com.hz.crm.domain.system.mapper.SystemParameterMapper;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemParameterApplicationService {

    public static final String KEY_FOLLOWUP_FIRST_DELAY_MINUTES =
            "sales.followup.inactivity.firstDelayMinutes";

    public static final String KEY_FOLLOWUP_SECOND_DELAY_MINUTES =
            "sales.followup.inactivity.secondDelayMinutes";

    private static final String GROUP_SALES_TASK = "SALES_TASK";

    private static final String VALUE_TYPE_NUMBER = "NUMBER";

    @Autowired
    private SystemParameterMapper systemParameterMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Value("${crm.task.followup-inactivity.first-delay-minutes:720}")
    private int defaultFirstDelayMinutes;

    @Value("${crm.task.followup-inactivity.second-delay-minutes:1440}")
    private int defaultSecondDelayMinutes;

    @Transactional(readOnly = true)
    public FollowupTaskSettingsResponse followupTaskSettings(Long tenantId) {
        FollowupTaskSettingsResponse response = new FollowupTaskSettingsResponse();
        response.setDefaultFirstDelayMinutes(normalizeDelayMinutes(defaultFirstDelayMinutes, 720));
        response.setDefaultSecondDelayMinutes(normalizeDelayMinutes(defaultSecondDelayMinutes, 1440));
        response.setFirstDelayMinutes(followupFirstDelayMinutes(tenantId));
        response.setSecondDelayMinutes(followupSecondDelayMinutes(tenantId));
        return response;
    }

    @Transactional
    public FollowupTaskSettingsResponse saveFollowupTaskSettings(
            Long tenantId, Long operatorId, FollowupTaskSettingsSaveRequest request) {
        if (request == null) {
            throw new BusinessException("SYSTEM_PARAMETER_001", "系统参数不能为空");
        }
        int firstDelayMinutes = normalizeDelayMinutes(request.getFirstDelayMinutes(), 0);
        int secondDelayMinutes = normalizeDelayMinutes(request.getSecondDelayMinutes(), 0);
        if (firstDelayMinutes <= 0 || secondDelayMinutes <= 0) {
            throw new BusinessException("SYSTEM_PARAMETER_002", "跟进提醒间隔必须大于0分钟");
        }
        saveParameter(
                tenantId,
                KEY_FOLLOWUP_FIRST_DELAY_MINUTES,
                String.valueOf(firstDelayMinutes),
                "第一次跟进提醒间隔",
                "销售写完跟进后，超过该分钟数仍未产生新跟进时生成第一次提醒",
                GROUP_SALES_TASK,
                VALUE_TYPE_NUMBER,
                Integer.valueOf(10),
                operatorId);
        saveParameter(
                tenantId,
                KEY_FOLLOWUP_SECOND_DELAY_MINUTES,
                String.valueOf(secondDelayMinutes),
                "第二次跟进提醒间隔",
                "销售写完跟进后，超过该分钟数仍未产生新跟进时生成第二次强提醒",
                GROUP_SALES_TASK,
                VALUE_TYPE_NUMBER,
                Integer.valueOf(20),
                operatorId);
        return followupTaskSettings(tenantId);
    }

    @Transactional(readOnly = true)
    public int followupFirstDelayMinutes(Long tenantId) {
        int defaultValue = normalizeDelayMinutes(defaultFirstDelayMinutes, 720);
        return intValue(tenantId, KEY_FOLLOWUP_FIRST_DELAY_MINUTES, defaultValue);
    }

    @Transactional(readOnly = true)
    public int followupSecondDelayMinutes(Long tenantId) {
        int defaultValue = normalizeDelayMinutes(defaultSecondDelayMinutes, 1440);
        return intValue(tenantId, KEY_FOLLOWUP_SECOND_DELAY_MINUTES, defaultValue);
    }

    @Transactional(readOnly = true)
    public int intValue(Long tenantId, String paramKey, int defaultValue) {
        SystemParameterEntity entity = findOne(tenantId, paramKey);
        if (entity == null || !StringUtils.hasText(entity.getParamValue())) {
            return defaultValue;
        }
        try {
            return normalizeDelayMinutes(Integer.parseInt(entity.getParamValue().trim()), defaultValue);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private void saveParameter(
            Long tenantId,
            String paramKey,
            String paramValue,
            String name,
            String description,
            String groupCode,
            String valueType,
            Integer sortNo,
            Long operatorId) {
        LocalDateTime now = DateTimes.now();
        SystemParameterEntity entity = findOneIncludeDeleted(tenantId, paramKey);
        if (entity == null) {
            entity = new SystemParameterEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setCreatedAt(now);
        }
        entity.setDeleted(false);
        entity.setParamKey(paramKey);
        entity.setParamValue(paramValue);
        entity.setName(name);
        entity.setDescription(description);
        entity.setGroupCode(groupCode);
        entity.setValueType(valueType);
        entity.setSortNo(sortNo);
        entity.setUpdatedAt(now);
        if (entity.getId() == null) {
            return;
        }
        if (findOneIncludeDeleted(tenantId, paramKey) == null) {
            systemParameterMapper.insert(entity);
        } else {
            systemParameterMapper.updateById(entity);
        }
    }

    private SystemParameterEntity findOne(Long tenantId, String paramKey) {
        QueryWrapper<SystemParameterEntity> wrapper = new QueryWrapper<SystemParameterEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("param_key", paramKey);
        wrapper.eq("deleted", false);
        wrapper.last("limit 1");
        return systemParameterMapper.selectOne(wrapper);
    }

    private SystemParameterEntity findOneIncludeDeleted(Long tenantId, String paramKey) {
        QueryWrapper<SystemParameterEntity> wrapper = new QueryWrapper<SystemParameterEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("param_key", paramKey);
        wrapper.last("limit 1");
        return systemParameterMapper.selectOne(wrapper);
    }

    private int normalizeDelayMinutes(Integer configuredMinutes, int defaultMinutes) {
        if (configuredMinutes == null || configuredMinutes.intValue() <= 0) {
            return defaultMinutes;
        }
        return configuredMinutes.intValue();
    }
}
