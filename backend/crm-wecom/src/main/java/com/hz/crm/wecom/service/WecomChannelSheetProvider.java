package com.hz.crm.wecom.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.application.channel.dto.ChannelSourceResponse;
import com.hz.crm.application.channel.sheet.ChannelSheetCreateCommand;
import com.hz.crm.application.channel.sheet.ChannelSheetDocument;
import com.hz.crm.application.channel.sheet.ChannelSheetFieldDefinition;
import com.hz.crm.application.channel.sheet.ChannelSheetProvider;
import com.hz.crm.application.channel.sheet.ChannelSheetRecord;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.wecom.WecomCorpConfigEntity;
import com.hz.crm.wecom.client.WecomApiClient;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class WecomChannelSheetProvider implements ChannelSheetProvider {

    private static final int PAGE_SIZE = 1000;

    @Autowired
    private WecomManageService wecomManageService;

    @Autowired
    private WecomTokenService wecomTokenService;

    @Autowired
    private WecomApiClient wecomApiClient;

    @Override
    public String platform() {
        return "WECOM";
    }

    @Override
    public ChannelSheetDocument create(ChannelSheetCreateCommand command) {
        WecomCorpConfigEntity config = resolveConfig(command.getTenantId(), command.getConfigId());
        String token = wecomTokenService.getToken(config);
        String adminUserId = wecomManageService.findWecomUserIdByCrmUserId(
                command.getTenantId(), config.getId(), command.getOperatorId());
        if (!StringUtils.hasText(adminUserId)) {
            throw new BusinessException(
                    "WECOM_SHEET_004",
                    "当前CRM账号尚未绑定企业微信成员，请先在企业微信配置中完成员工映射");
        }
        List<String> adminUsers = new ArrayList<String>();
        adminUsers.add(adminUserId);
        JSONObject created = wecomApiClient.createSmartSheet(
                token, command.getName(), adminUsers);
        String docId = firstText(created.getString("docid"), created.getString("doc_id"));
        String sourceUrl = firstText(created.getString("url"), created.getString("doc_url"));
        if (!StringUtils.hasText(docId) || !StringUtils.hasText(sourceUrl)) {
            throw new BusinessException("WECOM_SHEET_001", "企业微信创建智能表格后未返回文档信息");
        }
        String sheetId = resolveSheetId(
                wecomApiClient.getSmartSheetSheets(token, docId));
        if (!StringUtils.hasText(sheetId)) {
            throw new BusinessException("WECOM_SHEET_002", "企业微信智能表格未返回默认子表编号");
        }
        JSONArray fields = buildFields(command.getFields(), false);
        if (!fields.isEmpty()) {
            try {
                wecomApiClient.addSmartSheetFields(token, docId, sheetId, fields);
            } catch (BusinessException ex) {
                cleanupFailedDocument(token, docId);
                throw fieldOperationException("创建", command.getFields(), ex);
            }
        }
        ChannelSheetDocument document = new ChannelSheetDocument();
        document.setPlatform(platform());
        document.setConfigId(config.getId());
        document.setDocId(docId);
        document.setSheetId(sheetId);
        document.setSourceUrl(sourceUrl);
        return document;
    }

    @Override
    public List<ChannelSheetRecord> fetchRecords(Long tenantId, ChannelSourceResponse source) {
        WecomCorpConfigEntity config = resolveConfig(tenantId, source.getWecomConfigId());
        String token = wecomTokenService.getToken(config);
        List<ChannelSheetRecord> records = new ArrayList<ChannelSheetRecord>();
        int offset = 0;
        while (true) {
            JSONObject response = wecomApiClient.getSmartSheetRecords(
                    token, source.getDocId(), source.getSheetId(), offset, PAGE_SIZE);
            JSONArray items = response.getJSONArray("records");
            if (items == null || items.isEmpty()) {
                break;
            }
            for (int index = 0; index < items.size(); index++) {
                JSONObject item = items.getJSONObject(index);
                if (item != null) {
                    records.add(toRecord(item));
                }
            }
            offset += items.size();
            Integer total = response.getInteger("total");
            Boolean hasMore = response.getBoolean("has_more");
            if (Boolean.FALSE.equals(hasMore)
                    || items.size() < PAGE_SIZE
                    || total != null && offset >= total.intValue()) {
                break;
            }
        }
        return records;
    }

    @Override
    public List<ChannelSheetFieldDefinition> fetchFields(
            Long tenantId,
            ChannelSourceResponse source) {
        WecomCorpConfigEntity config = resolveConfig(tenantId, source.getWecomConfigId());
        String token = wecomTokenService.getToken(config);
        List<ChannelSheetFieldDefinition> fields = new ArrayList<ChannelSheetFieldDefinition>();
        int offset = 0;
        while (true) {
            JSONObject response = wecomApiClient.getSmartSheetFields(
                    token, source.getDocId(), source.getSheetId(), offset, PAGE_SIZE);
            JSONArray items = response.getJSONArray("fields");
            if (items == null || items.isEmpty()) {
                break;
            }
            for (int index = 0; index < items.size(); index++) {
                JSONObject item = items.getJSONObject(index);
                if (item == null) {
                    continue;
                }
                ChannelSheetFieldDefinition field = new ChannelSheetFieldDefinition();
                field.setFieldId(item.getString("field_id"));
                field.setTitle(item.getString("field_title"));
                field.setFieldType(item.getString("field_type"));
                field.setRequired(fields.isEmpty());
                fields.add(field);
            }
            offset += items.size();
            Integer next = response.getInteger("next");
            Integer total = response.getInteger("total");
            Boolean hasMore = response.getBoolean("has_more");
            if (Boolean.FALSE.equals(hasMore)
                    || items.size() < PAGE_SIZE
                    || total != null && offset >= total.intValue()) {
                break;
            }
            if (next != null && next.intValue() > offset) {
                offset = next.intValue();
            }
        }
        return fields;
    }

    @Override
    public void addFields(
            Long tenantId,
            ChannelSourceResponse source,
            List<ChannelSheetFieldDefinition> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        WecomCorpConfigEntity config = resolveConfig(tenantId, source.getWecomConfigId());
        String token = wecomTokenService.getToken(config);
        try {
            wecomApiClient.addSmartSheetFields(
                    token, source.getDocId(), source.getSheetId(), buildFields(fields, false));
        } catch (BusinessException ex) {
            throw fieldOperationException("新增", fields, ex);
        }
    }

    @Override
    public void updateFields(
            Long tenantId,
            ChannelSourceResponse source,
            List<ChannelSheetFieldDefinition> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        WecomCorpConfigEntity config = resolveConfig(tenantId, source.getWecomConfigId());
        String token = wecomTokenService.getToken(config);
        wecomApiClient.updateSmartSheetFields(
                token, source.getDocId(), source.getSheetId(), buildFields(fields, true));
    }

    @Override
    public void deleteFields(
            Long tenantId,
            ChannelSourceResponse source,
            List<String> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return;
        }
        WecomCorpConfigEntity config = resolveConfig(tenantId, source.getWecomConfigId());
        String token = wecomTokenService.getToken(config);
        wecomApiClient.deleteSmartSheetFields(
                token, source.getDocId(), source.getSheetId(), fieldIds);
    }

    @Override
    public void deleteDocument(Long tenantId, ChannelSourceResponse source) {
        WecomCorpConfigEntity config = resolveConfig(tenantId, source.getWecomConfigId());
        String token = wecomTokenService.getToken(config);
        wecomApiClient.deleteDocument(token, source.getDocId());
    }

    private WecomCorpConfigEntity resolveConfig(Long tenantId, Long configId) {
        WecomCorpConfigEntity config = configId == null
                ? wecomManageService.findFirstConfig(tenantId)
                : wecomManageService.findConfig(tenantId, configId);
        if (config == null || !config.isEnabled()) {
            throw new BusinessException("WECOM_SHEET_003", "请先在系统设置中启用企业微信配置");
        }
        return config;
    }

    private JSONArray buildFields(
            List<ChannelSheetFieldDefinition> definitions,
            boolean includeFieldId) {
        JSONArray fields = new JSONArray();
        if (definitions == null) {
            return fields;
        }
        for (ChannelSheetFieldDefinition definition : definitions) {
            if (definition == null || !StringUtils.hasText(definition.getTitle())) {
                continue;
            }
            JSONObject field = new JSONObject();
            if (includeFieldId && StringUtils.hasText(definition.getFieldId())) {
                field.put("field_id", definition.getFieldId().trim());
            }
            field.put("field_title", definition.getTitle().trim());
            String fieldType = StringUtils.hasText(definition.getFieldType())
                    ? definition.getFieldType().trim()
                    : "FIELD_TYPE_TEXT";
            field.put("field_type", fieldType);
            if (!includeFieldId) {
                appendDefaultFieldProperty(field, fieldType);
            }
            fields.add(field);
        }
        return fields;
    }

    private void appendDefaultFieldProperty(JSONObject field, String fieldType) {
        if ("FIELD_TYPE_NUMBER".equals(fieldType)) {
            JSONObject property = new JSONObject();
            property.put("decimal_places", 0);
            property.put("use_separate", false);
            field.put("property_number", property);
            return;
        }
        if ("FIELD_TYPE_DATE_TIME".equals(fieldType)) {
            JSONObject property = new JSONObject();
            property.put("format", "yyyy\"年\"m\"月\"d\"日\"");
            property.put("auto_fill", false);
            field.put("property_date_time", property);
            return;
        }
        if ("FIELD_TYPE_SINGLE_SELECT".equals(fieldType)) {
            JSONObject property = new JSONObject();
            property.put("is_quick_add", true);
            property.put("options", new JSONArray());
            field.put("property_single_select", property);
            return;
        }
        if ("FIELD_TYPE_SELECT".equals(fieldType)) {
            JSONObject property = new JSONObject();
            property.put("is_quick_add", true);
            property.put("options", new JSONArray());
            field.put("property_select", property);
            return;
        }
        if ("FIELD_TYPE_CHECKBOX".equals(fieldType)) {
            JSONObject property = new JSONObject();
            property.put("checked", false);
            field.put("property_checkbox", property);
            return;
        }
        if ("FIELD_TYPE_URL".equals(fieldType)) {
            JSONObject property = new JSONObject();
            property.put("type", "LINK_TYPE_PURE_TEXT");
            field.put("property_url", property);
        }
    }

    private BusinessException fieldOperationException(
            String operation,
            List<ChannelSheetFieldDefinition> definitions,
            BusinessException cause) {
        StringBuilder summary = new StringBuilder();
        if (definitions != null) {
            for (ChannelSheetFieldDefinition definition : definitions) {
                if (definition == null || !StringUtils.hasText(definition.getTitle())) {
                    continue;
                }
                if (summary.length() > 0) {
                    summary.append("、");
                }
                summary.append(definition.getTitle().trim())
                        .append('(')
                        .append(StringUtils.hasText(definition.getFieldType())
                                ? definition.getFieldType().trim()
                                : "FIELD_TYPE_TEXT")
                        .append(')');
            }
        }
        BusinessException exception = new BusinessException(
                cause.getCode(),
                "企业微信智能表格字段" + operation + "失败，字段：" + summary + "；" + cause.getMessage());
        exception.initCause(cause);
        return exception;
    }

    private void cleanupFailedDocument(String token, String docId) {
        try {
            wecomApiClient.deleteDocument(token, docId);
        } catch (RuntimeException cleanupException) {
            log.warn("企业微信智能表格创建失败后清理文档失败，文档编号：{}", docId, cleanupException);
        }
    }

    private String resolveSheetId(JSONObject response) {
        String direct = firstText(response.getString("sheet_id"), response.getString("sheetid"));
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        String[] arrayKeys = {"sheets", "properties", "sheet_list"};
        for (String key : arrayKeys) {
            JSONArray array = response.getJSONArray(key);
            if (array == null || array.isEmpty()) {
                continue;
            }
            JSONObject first = array.getJSONObject(0);
            if (first != null) {
                String sheetId = firstText(first.getString("sheet_id"), first.getString("sheetid"));
                if (StringUtils.hasText(sheetId)) {
                    return sheetId;
                }
                JSONObject properties = first.getJSONObject("properties");
                if (properties != null) {
                    sheetId = firstText(
                            properties.getString("sheet_id"), properties.getString("sheetid"));
                    if (StringUtils.hasText(sheetId)) {
                        return sheetId;
                    }
                }
            }
        }
        JSONObject properties = response.getJSONObject("properties");
        return properties == null
                ? null
                : firstText(properties.getString("sheet_id"), properties.getString("sheetid"));
    }

    private ChannelSheetRecord toRecord(JSONObject item) {
        ChannelSheetRecord record = new ChannelSheetRecord();
        record.setRecordId(firstText(item.getString("record_id"), item.getString("recordid")));
        record.setVersion(firstText(item.getString("update_time"), item.getString("updated_at")));
        record.setCreatedAt(parseTime(firstText(
                item.getString("create_time"), item.getString("created_at"))));
        record.setUpdatedAt(parseTime(firstText(
                item.getString("update_time"), item.getString("updated_at"))));
        JSONObject values = item.getJSONObject("values");
        if (values != null) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                record.getValues().put(entry.getKey(), flattenValue(entry.getValue()));
            }
        }
        return record;
    }

    private String flattenValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<String> values = new ArrayList<String>();
            for (int index = 0; index < array.size(); index++) {
                String text = flattenValue(array.get(index));
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
            return String.join("、", values);
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String[] keys = {"text", "value", "name", "user_name", "date_time", "number", "link"};
            for (String key : keys) {
                Object nested = object.get(key);
                if (nested != null && !(nested instanceof JSONObject) && !(nested instanceof JSONArray)) {
                    return String.valueOf(nested);
                }
            }
            return object.toJSONString();
        }
        return String.valueOf(value);
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long timestamp = Long.parseLong(value.trim());
            if (timestamp < 100000000000L) {
                timestamp = timestamp * 1000L;
            }
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        } catch (Exception ex) {
            try {
                return LocalDateTime.parse(value.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : second;
    }
}
