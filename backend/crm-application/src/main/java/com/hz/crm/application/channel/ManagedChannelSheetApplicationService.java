package com.hz.crm.application.channel;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.application.channel.dto.ChannelSourceResponse;
import com.hz.crm.application.channel.dto.ChannelSourceSyncResult;
import com.hz.crm.application.channel.dto.ExternalChannelSyncRequest;
import com.hz.crm.application.channel.dto.ExternalChannelSyncResult;
import com.hz.crm.application.channel.dto.ManagedChannelSheetCreateRequest;
import com.hz.crm.application.channel.dto.ManagedChannelSheetFieldRequest;
import com.hz.crm.application.channel.sheet.ChannelSheetCreateCommand;
import com.hz.crm.application.channel.sheet.ChannelSheetDocument;
import com.hz.crm.application.channel.sheet.ChannelSheetFieldDefinition;
import com.hz.crm.application.channel.sheet.ChannelSheetProvider;
import com.hz.crm.application.channel.sheet.ChannelSheetRecord;
import com.hz.crm.application.system.MessageWebhookApplicationService;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.channel.ChannelSyncLogEntity;
import com.hz.crm.domain.channel.ChannelSyncTrigger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ManagedChannelSheetApplicationService {

    private static final String DEFAULT_FIELD_MAPPING = "{\"companyName\":\"公司名称\",\"contactName\":\"联系人\",\"phone\":\"联系电话\",\"email\":\"联系邮箱\",\"remark\":\"需求描述\"}";

    private static final int MAX_FIELD_COUNT = 50;

    private static final List<String> BUSINESS_KEYS = Arrays.asList(
            "companyName", "contactName", "phone", "email", "remark");

    private static final List<String> CUSTOMER_IDENTITY_KEYS = Arrays.asList(
            "companyName", "contactName", "phone", "email");

    private static final List<String> ALLOWED_FIELD_TYPES = Arrays.asList(
            "FIELD_TYPE_TEXT",
            "FIELD_TYPE_PHONE_NUMBER",
            "FIELD_TYPE_EMAIL",
            "FIELD_TYPE_NUMBER",
            "FIELD_TYPE_DATE_TIME",
            "FIELD_TYPE_SINGLE_SELECT",
            "FIELD_TYPE_SELECT",
            "FIELD_TYPE_CHECKBOX",
            "FIELD_TYPE_URL");

    @Autowired(required = false)
    private List<ChannelSheetProvider> providers;

    @Autowired
    private ChannelSourceApplicationService channelSourceApplicationService;

    @Autowired
    private ChannelApplicationService channelApplicationService;

    @Autowired
    private MessageWebhookApplicationService messageWebhookApplicationService;

    public ChannelSourceResponse create(
            Long tenantId,
            Long operatorId,
            ManagedChannelSheetCreateRequest request) {
        ChannelSheetProvider provider = requireProvider(request.getPlatform());
        ChannelSheetCreateCommand command = new ChannelSheetCreateCommand();
        command.setTenantId(tenantId);
        command.setOperatorId(operatorId);
        command.setConfigId(request.getConfigId());
        command.setName(request.getName().trim());
        List<ChannelSheetFieldDefinition> fields = normalizeFields(request.getFields());
        command.setFields(fields);
        ChannelSheetDocument document = provider.create(command);
        return channelSourceApplicationService.registerManagedSheet(
                tenantId,
                operatorId,
                request.getName(),
                request.getProductId(),
                buildFieldMapping(fields),
                document);
    }

    public ChannelSourceSyncResult sync(Long tenantId, Long operatorId, Long sourceId) {
        ChannelSourceResponse source = channelSourceApplicationService.detail(tenantId, sourceId);
        String platform = resolvePlatform(source.getExternalProvider());
        ChannelSheetProvider provider = requireProvider(platform);
        ChannelSyncLogEntity log = channelSourceApplicationService.startLog(
                tenantId, sourceId, ChannelSyncTrigger.MANUAL);
        try {
            List<ChannelSheetRecord> records = provider.fetchRecords(tenantId, source);
            ChannelSourceSyncResult result = importRecords(tenantId, source, records);
            result.setSourceId(sourceId);
            result.setLogId(log.getId());
            ChannelSourceSyncResult completed = channelSourceApplicationService.completeLog(
                    tenantId,
                    sourceId,
                    log.getId(),
                    result,
                    buildFieldSnapshot(records));
            messageWebhookApplicationService.notifyPublicPoolCreated(
                    tenantId, completed.getCreatedCount(), source.getName());
            return completed;
        } catch (RuntimeException ex) {
            channelSourceApplicationService.failLog(
                    tenantId, sourceId, log.getId(), ex.getMessage());
            throw ex;
        }
    }

    private ChannelSourceSyncResult importRecords(
            Long tenantId,
            ChannelSourceResponse source,
            List<ChannelSheetRecord> records) {
        List<ChannelSheetRecord> safeRecords = records == null
                ? Collections.<ChannelSheetRecord>emptyList()
                : records;
        Map<String, String> mapping = parseMapping(source.getFieldMappingJson());
        ChannelSourceSyncResult result = new ChannelSourceSyncResult();
        result.setFetchedCount(safeRecords.size());
        for (ChannelSheetRecord record : safeRecords) {
            if (!hasCustomerIdentity(record, mapping)) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }
            try {
                ExternalChannelSyncResult syncResult = channelApplicationService.syncExternalChannel(
                        tenantId, buildSyncRequest(source, mapping, record));
                if (syncResult.isCreated()) {
                    result.setCreatedCount(result.getCreatedCount() + 1);
                } else if (syncResult.isUpdated()) {
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                }
            } catch (RuntimeException ex) {
                result.setFailedCount(result.getFailedCount() + 1);
                log.warn(
                        "自建表格记录同步失败，渠道来源编号：{}，平台记录编号：{}",
                        source.getId(),
                        record == null ? null : record.getRecordId(),
                        ex);
            }
        }
        return result;
    }

    private boolean hasCustomerIdentity(
            ChannelSheetRecord record,
            Map<String, String> mapping) {
        if (record == null || record.getValues() == null) {
            return false;
        }
        Map<String, String> values = record.getValues();
        return StringUtils.hasText(mappedValue(values, mapping, "companyName"))
                || StringUtils.hasText(mappedValue(values, mapping, "contactName"))
                || StringUtils.hasText(mappedValue(values, mapping, "phone"))
                || StringUtils.hasText(mappedValue(values, mapping, "email"));
    }

    private ExternalChannelSyncRequest buildSyncRequest(
            ChannelSourceResponse source,
            Map<String, String> mapping,
            ChannelSheetRecord record) {
        Map<String, String> values = record.getValues() == null
                ? Collections.<String, String>emptyMap()
                : record.getValues();
        String companyName = mappedValue(values, mapping, "companyName");
        String contactName = mappedValue(values, mapping, "contactName");
        String phone = mappedValue(values, mapping, "phone");
        String email = mappedValue(values, mapping, "email");
        String remark = mappedValue(values, mapping, "remark");
        String snapshot = JSON.toJSONString(values);
        ExternalChannelSyncRequest request = new ExternalChannelSyncRequest();
        request.setExternalProvider(source.getExternalProvider());
        request.setExternalKey(source.getId() + ":" + requireRecordId(record));
        request.setExternalVersion(firstText(record.getVersion(), sha256(snapshot)));
        request.setTitle(limitText(firstText(contactName, companyName, "智能表格记录"), 128));
        request.setSource("WECOM".equals(resolvePlatform(source.getExternalProvider())) ? "WECHAT" : "OTHER");
        request.setContactName(limitText(contactName, 128));
        request.setCompanyName(limitText(companyName, 128));
        request.setPhone(limitText(phone, 32));
        request.setEmail(limitText(email, 128));
        request.setProductId(source.getProductId());
        request.setRemark(buildRemark(values, remark));
        request.setOccurredAt(record.getCreatedAt());
        request.setSourceSnapshot(snapshot);
        return request;
    }

    private List<ChannelSheetFieldDefinition> defaultFields() {
        List<ChannelSheetFieldDefinition> fields = new ArrayList<ChannelSheetFieldDefinition>();
        fields.add(field("companyName", "公司名称", "FIELD_TYPE_TEXT"));
        fields.add(field("contactName", "联系人", "FIELD_TYPE_TEXT"));
        fields.add(field("phone", "联系电话", "FIELD_TYPE_PHONE_NUMBER"));
        fields.add(field("email", "联系邮箱", "FIELD_TYPE_EMAIL"));
        fields.add(field("remark", "需求描述", "FIELD_TYPE_TEXT"));
        return fields;
    }

    private List<ChannelSheetFieldDefinition> normalizeFields(
            List<ManagedChannelSheetFieldRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return defaultFields();
        }
        if (requests.size() > MAX_FIELD_COUNT) {
            throw new BusinessException(
                    "CHANNEL_MANAGED_SHEET_004",
                    "自定义字段不能超过" + MAX_FIELD_COUNT + "个");
        }
        List<ChannelSheetFieldDefinition> fields = new ArrayList<ChannelSheetFieldDefinition>();
        Set<String> titles = new HashSet<String>();
        Set<String> businessKeys = new HashSet<String>();
        for (ManagedChannelSheetFieldRequest request : requests) {
            if (request == null) {
                continue;
            }
            String title = trimToNull(request.getTitle());
            if (title == null) {
                throw new BusinessException("CHANNEL_MANAGED_SHEET_005", "字段名称不能为空");
            }
            if (title.length() > 64) {
                throw new BusinessException(
                        "CHANNEL_MANAGED_SHEET_006",
                        "字段名称不能超过64个字符：" + title);
            }
            String normalizedTitle = title.toLowerCase(Locale.ROOT);
            if (!titles.add(normalizedTitle)) {
                throw new BusinessException(
                        "CHANNEL_MANAGED_SHEET_007",
                        "字段名称不能重复：" + title);
            }
            String fieldType = normalizePlatform(request.getFieldType());
            if (!ALLOWED_FIELD_TYPES.contains(fieldType)) {
                throw new BusinessException(
                        "CHANNEL_MANAGED_SHEET_008",
                        "暂不支持该字段类型：" + request.getFieldType());
            }
            String businessKey = trimToNull(request.getBusinessKey());
            if (businessKey != null && !BUSINESS_KEYS.contains(businessKey)) {
                businessKey = null;
            }
            if (businessKey != null && !businessKeys.add(businessKey)) {
                throw new BusinessException(
                        "CHANNEL_MANAGED_SHEET_009",
                        "同一业务字段不能重复配置：" + title);
            }
            fields.add(field(businessKey, title, fieldType));
        }
        if (fields.isEmpty()) {
            throw new BusinessException("CHANNEL_MANAGED_SHEET_010", "至少需要配置一个字段");
        }
        boolean hasIdentityField = false;
        for (String identityKey : CUSTOMER_IDENTITY_KEYS) {
            if (businessKeys.contains(identityKey)) {
                hasIdentityField = true;
                break;
            }
        }
        if (!hasIdentityField) {
            throw new BusinessException(
                    "CHANNEL_MANAGED_SHEET_011",
                    "公司名称、联系人、联系电话、联系邮箱至少保留一个");
        }
        return fields;
    }

    private String buildFieldMapping(List<ChannelSheetFieldDefinition> fields) {
        JSONObject mapping = new JSONObject();
        if (fields != null) {
            for (ChannelSheetFieldDefinition field : fields) {
                if (field != null
                        && BUSINESS_KEYS.contains(field.getBusinessKey())
                        && StringUtils.hasText(field.getTitle())) {
                    mapping.put(field.getBusinessKey(), field.getTitle().trim());
                }
            }
        }
        return mapping.isEmpty() ? DEFAULT_FIELD_MAPPING : mapping.toJSONString();
    }

    private ChannelSheetFieldDefinition field(String businessKey, String title, String fieldType) {
        ChannelSheetFieldDefinition field = new ChannelSheetFieldDefinition();
        field.setBusinessKey(businessKey);
        field.setTitle(title);
        field.setFieldType(fieldType);
        return field;
    }

    private ChannelSheetProvider requireProvider(String platform) {
        String normalized = normalizePlatform(platform);
        if (providers != null) {
            for (ChannelSheetProvider provider : providers) {
                if (provider != null && normalized.equals(normalizePlatform(provider.platform()))) {
                    return provider;
                }
            }
        }
        throw new BusinessException(
                "CHANNEL_MANAGED_SHEET_002",
                "当前尚未接入" + platformName(normalized) + "自建表格能力");
    }

    private String resolvePlatform(String externalProvider) {
        String value = normalizePlatform(externalProvider);
        int index = value.indexOf('_');
        return index > 0 ? value.substring(0, index) : value;
    }

    private String normalizePlatform(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String platformName(String platform) {
        if ("WECOM".equals(platform)) {
            return "企业微信";
        }
        if ("DINGTALK".equals(platform)) {
            return "钉钉";
        }
        if ("FEISHU".equals(platform)) {
            return "飞书";
        }
        return platform;
    }

    private Map<String, String> parseMapping(String mappingJson) {
        JSONObject object = JSON.parseObject(StringUtils.hasText(mappingJson)
                ? mappingJson
                : DEFAULT_FIELD_MAPPING);
        Map<String, String> mapping = new LinkedHashMap<String, String>();
        if (object != null) {
            for (String key : object.keySet()) {
                mapping.put(key, object.getString(key));
            }
        }
        return mapping;
    }

    private String mappedValue(
            Map<String, String> values,
            Map<String, String> mapping,
            String businessKey) {
        String title = mapping.get(businessKey);
        return title == null ? null : trimToNull(values.get(title));
    }

    private String buildRemark(Map<String, String> values, String demand) {
        if (values == null || values.isEmpty()) {
            return trimToNull(demand);
        }
        StringBuilder builder = new StringBuilder("### 表格填写信息\n");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = trimToNull(entry.getValue());
            if (value != null) {
                builder.append("- ").append(entry.getKey()).append("：").append(value).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private String buildFieldSnapshot(List<ChannelSheetRecord> records) {
        if (records == null || records.isEmpty() || records.get(0).getValues() == null) {
            return "[]";
        }
        return JSON.toJSONString(new ArrayList<String>(records.get(0).getValues().keySet()));
    }

    private String requireRecordId(ChannelSheetRecord record) {
        if (record == null || !StringUtils.hasText(record.getRecordId())) {
            throw new BusinessException("CHANNEL_MANAGED_SHEET_003", "平台表格记录缺少记录编号");
        }
        return record.getRecordId().trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", Integer.valueOf(item & 0xff)));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("渠道数据指纹生成失败", ex);
        }
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
