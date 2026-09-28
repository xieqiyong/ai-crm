package com.hz.crm.application.system;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.system.dto.FollowupTaskSettingsResponse;
import com.hz.crm.application.system.dto.FollowupTaskSettingsSaveRequest;
import com.hz.crm.application.system.dto.MessageWebhookSettingsResponse;
import com.hz.crm.application.system.dto.MessageWebhookSettingsSaveRequest;
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

    public static final String KEY_MESSAGE_WEBHOOK_ENABLED = "message.webhook.enabled";

    public static final String KEY_MESSAGE_WEBHOOK_PLATFORM = "message.webhook.platform";

    public static final String KEY_MESSAGE_WEBHOOK_URL = "message.webhook.url";

    public static final String KEY_MESSAGE_WEBHOOK_PUBLIC_POOL = "message.webhook.publicPool.enabled";

    private static final String GROUP_SALES_TASK = "SALES_TASK";

    private static final String GROUP_MESSAGE = "MESSAGE";

    private static final String VALUE_TYPE_NUMBER = "NUMBER";

    private static final String VALUE_TYPE_STRING = "STRING";

    private static final String VALUE_TYPE_BOOLEAN = "BOOLEAN";

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
    public MessageWebhookSettingsResponse messageWebhookSettings(Long tenantId) {
        MessageWebhookSettingsResponse response = new MessageWebhookSettingsResponse();
        response.setEnabled(booleanValue(tenantId, KEY_MESSAGE_WEBHOOK_ENABLED, false));
        response.setPlatform(stringValue(tenantId, KEY_MESSAGE_WEBHOOK_PLATFORM, "DINGTALK"));
        response.setWebhookUrl(stringValue(tenantId, KEY_MESSAGE_WEBHOOK_URL, ""));
        response.setNotifyPublicPool(booleanValue(tenantId, KEY_MESSAGE_WEBHOOK_PUBLIC_POOL, true));
        return response;
    }

    @Transactional
    public MessageWebhookSettingsResponse saveMessageWebhookSettings(
            Long tenantId, Long operatorId, MessageWebhookSettingsSaveRequest request) {
        if (request == null) {
            throw new BusinessException("SYSTEM_PARAMETER_003", "消息Webhook设置不能为空");
        }
        String platform = StringUtils.hasText(request.getPlatform())
                ? request.getPlatform().trim().toUpperCase()
                : "DINGTALK";
        if (!"DINGTALK".equals(platform) && !"FEISHU".equals(platform)) {
            throw new BusinessException("SYSTEM_PARAMETER_004", "消息平台只支持钉钉或飞书");
        }
        String webhookUrl = request.getWebhookUrl() == null ? "" : request.getWebhookUrl().trim();
        if (request.isEnabled() && !StringUtils.hasText(webhookUrl)) {
            throw new BusinessException("SYSTEM_PARAMETER_005", "启用消息Webhook前请填写机器人地址");
        }
        saveParameter(tenantId, KEY_MESSAGE_WEBHOOK_ENABLED, String.valueOf(request.isEnabled()),
                "消息Webhook开关", "是否启用外部机器人通知", GROUP_MESSAGE,
                VALUE_TYPE_BOOLEAN, Integer.valueOf(10), operatorId);
        saveParameter(tenantId, KEY_MESSAGE_WEBHOOK_PLATFORM, platform,
                "消息Webhook平台", "外部机器人平台", GROUP_MESSAGE,
                VALUE_TYPE_STRING, Integer.valueOf(20), operatorId);
        saveParameter(tenantId, KEY_MESSAGE_WEBHOOK_URL, webhookUrl,
                "消息Webhook地址", "钉钉或飞书群机器人的Webhook地址", GROUP_MESSAGE,
                VALUE_TYPE_STRING, Integer.valueOf(30), operatorId);
        saveParameter(tenantId, KEY_MESSAGE_WEBHOOK_PUBLIC_POOL,
                String.valueOf(request.isNotifyPublicPool()),
                "公海池新增通知", "公海池新增数据时通知销售关注", GROUP_MESSAGE,
                VALUE_TYPE_BOOLEAN, Integer.valueOf(40), operatorId);
        return messageWebhookSettings(tenantId);
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

    @Transactional(readOnly = true)
    public String stringValue(Long tenantId, String paramKey, String defaultValue) {
        SystemParameterEntity entity = findOne(tenantId, paramKey);
        if (entity == null || entity.getParamValue() == null) {
            return defaultValue;
        }
        return entity.getParamValue();
    }

    @Transactional(readOnly = true)
    public boolean booleanValue(Long tenantId, String paramKey, boolean defaultValue) {
        String value = stringValue(tenantId, paramKey, null);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
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
