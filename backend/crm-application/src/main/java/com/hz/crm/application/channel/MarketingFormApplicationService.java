package com.hz.crm.application.channel;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.channel.dto.MarketingFormFieldRequest;
import com.hz.crm.application.channel.dto.MarketingFormFieldResponse;
import com.hz.crm.application.channel.dto.MarketingFormQuery;
import com.hz.crm.application.channel.dto.MarketingFormResponse;
import com.hz.crm.application.channel.dto.MarketingFormSaveRequest;
import com.hz.crm.application.channel.dto.PublicMarketingFormResponse;
import com.hz.crm.application.channel.dto.PublicMarketingFormSubmitRequest;
import com.hz.crm.application.channel.dto.PublicMarketingFormSubmitResponse;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.ChannelSource;
import com.hz.crm.domain.channel.ChannelStatus;
import com.hz.crm.domain.channel.ChannelType;
import com.hz.crm.domain.channel.MarketingFormEntity;
import com.hz.crm.domain.channel.MarketingFormFieldEntity;
import com.hz.crm.domain.channel.MarketingFormFieldType;
import com.hz.crm.domain.channel.MarketingFormStatus;
import com.hz.crm.domain.channel.MarketingFormSubmissionEntity;
import com.hz.crm.domain.channel.mapper.ChannelRecordMapper;
import com.hz.crm.domain.channel.mapper.MarketingFormFieldMapper;
import com.hz.crm.domain.channel.mapper.MarketingFormMapper;
import com.hz.crm.domain.channel.mapper.MarketingFormSubmissionMapper;
import com.hz.crm.domain.common.BaseEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MarketingFormApplicationService {

    @Autowired
    private MarketingFormMapper marketingFormMapper;

    @Autowired
    private MarketingFormFieldMapper marketingFormFieldMapper;

    @Autowired
    private MarketingFormSubmissionMapper marketingFormSubmissionMapper;

    @Autowired
    private ChannelRecordMapper channelRecordMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(readOnly = true)
    public PageData<MarketingFormResponse> page(Long tenantId, Long userId, String dataScope, MarketingFormQuery query) {
        MarketingFormQuery safeQuery = query == null ? new MarketingFormQuery() : query;
        LambdaQueryWrapper<MarketingFormEntity> wrapper = new LambdaQueryWrapper<MarketingFormEntity>()
                .eq(MarketingFormEntity::getTenantId, tenantId)
                .eq(MarketingFormEntity::isDeleted, false)
                .orderByDesc(MarketingFormEntity::getCreatedAt);
        if ("SELF".equals(dataScope)) {
            wrapper.eq(MarketingFormEntity::getOwnerId, userId);
        }
        if (safeQuery.getStatus() != null) {
            wrapper.eq(MarketingFormEntity::getStatus, safeQuery.getStatus());
        }
        if (StringUtils.hasText(safeQuery.getKeyword())) {
            wrapper.and(item -> item.like(MarketingFormEntity::getTitle, safeQuery.getKeyword().trim())
                    .or()
                    .like(MarketingFormEntity::getSource, safeQuery.getKeyword().trim()));
        }
        List<MarketingFormEntity> all = marketingFormMapper.selectList(wrapper);
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        int fromIndex = Math.min((pageNo - 1) * pageSize, all.size());
        int toIndex = Math.min(fromIndex + pageSize, all.size());
        List<MarketingFormResponse> records = new ArrayList<MarketingFormResponse>();
        for (MarketingFormEntity entity : all.subList(fromIndex, toIndex)) {
            records.add(toResponse(entity, false));
        }
        return PageData.of(all.size(), pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public MarketingFormResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        MarketingFormEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        return toResponse(entity, true);
    }

    @Transactional
    public MarketingFormResponse save(Long tenantId, Long operatorId, String dataScope, MarketingFormSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BusinessException("MARKETING_FORM_001", "表单标题不能为空");
        }
        MarketingFormEntity entity;
        if (request.getId() == null) {
            entity = new MarketingFormEntity();
            prepareNew(entity, tenantId);
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setFormCode(nextFormCode());
            entity.setOwnerId(resolveOwnerId(operatorId, dataScope, request.getOwnerId()));
        } else {
            entity = findOne(tenantId, request.getId());
            checkDataScope(operatorId, dataScope, entity.getOwnerId());
            touch(entity);
        }
        entity.setTitle(trimToNull(request.getTitle()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setSource(normalizeSource(request.getSource()));
        entity.setSubmitMessage(resolveSubmitMessage(request.getSubmitMessage()));
        entity.setStatus(request.getStatus() == null ? MarketingFormStatus.PUBLISHED : request.getStatus());
        entity.setAutoCreateLead(request.isAutoCreateLead());
        if (request.getOwnerId() != null && !"SELF".equals(dataScope)) {
            entity.setOwnerId(request.getOwnerId());
        }
        if (request.getId() == null) {
            marketingFormMapper.insert(entity);
        } else {
            marketingFormMapper.updateById(entity);
        }
        replaceFields(tenantId, entity.getId(), resolveFields(request.getFields()));
        return toResponse(entity, true);
    }

    @Transactional
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        MarketingFormEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        touch(entity);
        marketingFormMapper.updateById(entity);
        List<MarketingFormFieldEntity> fields = formFields(tenantId, id);
        for (MarketingFormFieldEntity field : fields) {
            field.setDeleted(true);
            touch(field);
            marketingFormFieldMapper.updateById(field);
        }
    }

    @Transactional
    public PublicMarketingFormResponse publicDetail(String formCode) {
        MarketingFormEntity entity = findPublished(formCode);
        entity.setViewCount(safeLong(entity.getViewCount()) + 1L);
        touch(entity);
        marketingFormMapper.updateById(entity);
        PublicMarketingFormResponse response = new PublicMarketingFormResponse();
        response.setFormCode(entity.getFormCode());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setSubmitMessage(entity.getSubmitMessage());
        response.setFields(publicFieldResponses(formFields(entity.getTenantId(), entity.getId())));
        return response;
    }

    @Transactional
    public PublicMarketingFormSubmitResponse submit(
            PublicMarketingFormSubmitRequest request, String visitorIp, String userAgent) {
        if (request == null || !StringUtils.hasText(request.getFormCode())) {
            throw new BusinessException("MARKETING_FORM_002", "表单地址无效");
        }
        MarketingFormEntity form = findPublished(request.getFormCode());
        List<MarketingFormFieldEntity> fields = formFields(form.getTenantId(), form.getId());
        Map<String, String> values = request.getValues() == null
                ? new HashMap<String, String>()
                : request.getValues();
        validateRequired(fields, values);
        String contactName = mappedValue(fields, values, "name");
        String companyName = mappedValue(fields, values, "companyName");
        String phone = mappedValue(fields, values, "phone");
        String email = mappedValue(fields, values, "email");
        String remark = mappedValue(fields, values, "remark");
        ChannelRecordEntity channel = createChannel(form, values, contactName, companyName, phone, email, remark);
        Long leadId = channel.getLeadId();
        createSubmission(form, channel.getId(), leadId, values, contactName, companyName, phone, email, visitorIp, userAgent);
        form.setSubmitCount(safeLong(form.getSubmitCount()) + 1L);
        touch(form);
        marketingFormMapper.updateById(form);
        PublicMarketingFormSubmitResponse response = new PublicMarketingFormSubmitResponse();
        response.setSubmitted(true);
        response.setLeadCreated(false);
        response.setChannelId(channel.getId());
        response.setLeadId(leadId);
        response.setMessage(resolveSubmitMessage(form.getSubmitMessage()));
        return response;
    }

    private ChannelRecordEntity createChannel(
            MarketingFormEntity form,
            Map<String, String> values,
            String contactName,
            String companyName,
            String phone,
            String email,
            String remark) {
        ChannelRecordEntity duplicate = findDuplicateChannel(form.getTenantId(), companyName, phone);
        if (duplicate != null) {
            return duplicate;
        }
        ChannelRecordEntity entity = new ChannelRecordEntity();
        prepareNew(entity, form.getTenantId());
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTitle(shrink(resolveChannelTitle(form, contactName, companyName), 128));
        entity.setChannelType(ChannelType.FORM);
        entity.setStatus(ChannelStatus.NEW);
        entity.setSource(resolveSource(form));
        entity.setContactName(trimToNull(contactName));
        entity.setCompanyName(trimToNull(companyName));
        entity.setPhone(trimToNull(phone));
        entity.setEmail(trimToNull(email));
        entity.setRemark(shrink(resolveChannelRemark(form, remark), 512));
        entity.setUsefulInfo(JSON.toJSONString(values));
        channelRecordMapper.insert(entity);
        return entity;
    }

    private ChannelRecordEntity findDuplicateChannel(Long tenantId, String companyName, String phone) {
        String normalizedPhone = normalizePhone(phone);
        String normalizedCompanyName = normalizeCompanyName(companyName);
        if (normalizedPhone == null && normalizedCompanyName == null) {
            return null;
        }
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.and(condition -> {
            boolean hasPhone = normalizedPhone != null;
            if (hasPhone) {
                condition.apply(
                        "regexp_replace(coalesce(phone, ''), '[^0-9]', '', 'g') = {0}",
                        normalizedPhone);
            }
            if (normalizedCompanyName != null) {
                if (hasPhone) {
                    condition.or();
                }
                condition.apply(
                        "regexp_replace(lower(coalesce(company_name, '')), '\\s+', '', 'g') = {0}",
                        normalizedCompanyName);
            }
        });
        wrapper.orderByAsc("created_at").orderByAsc("id").last("limit 1");
        return channelRecordMapper.selectOne(wrapper);
    }

    private void createSubmission(
            MarketingFormEntity form,
            Long channelId,
            Long leadId,
            Map<String, String> values,
            String contactName,
            String companyName,
            String phone,
            String email,
            String visitorIp,
            String userAgent) {
        MarketingFormSubmissionEntity entity = new MarketingFormSubmissionEntity();
        prepareNew(entity, form.getTenantId());
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setFormId(form.getId());
        entity.setFormCode(form.getFormCode());
        entity.setChannelId(channelId);
        entity.setLeadId(leadId);
        entity.setContactName(trimToNull(contactName));
        entity.setCompanyName(trimToNull(companyName));
        entity.setPhone(trimToNull(phone));
        entity.setEmail(trimToNull(email));
        entity.setVisitorIp(shrink(visitorIp, 64));
        entity.setUserAgent(shrink(userAgent, 256));
        entity.setPayloadJson(JSON.toJSONString(values));
        marketingFormSubmissionMapper.insert(entity);
    }

    private MarketingFormEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("MARKETING_FORM_003", "表单编号不能为空");
        }
        MarketingFormEntity entity = marketingFormMapper.selectById(id);
        if (entity == null || entity.isDeleted() || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException("MARKETING_FORM_004", "表单不存在");
        }
        return entity;
    }

    private MarketingFormEntity findPublished(String formCode) {
        if (!StringUtils.hasText(formCode)) {
            throw new BusinessException("MARKETING_FORM_002", "表单地址无效");
        }
        MarketingFormEntity entity = marketingFormMapper.selectOne(new LambdaQueryWrapper<MarketingFormEntity>()
                .eq(MarketingFormEntity::getFormCode, formCode.trim())
                .eq(MarketingFormEntity::isDeleted, false));
        if (entity == null || MarketingFormStatus.PUBLISHED != entity.getStatus()) {
            throw new BusinessException("MARKETING_FORM_005", "表单不存在或未发布");
        }
        return entity;
    }

    private List<MarketingFormFieldEntity> formFields(Long tenantId, Long formId) {
        List<MarketingFormFieldEntity> fields = marketingFormFieldMapper.selectList(
                new LambdaQueryWrapper<MarketingFormFieldEntity>()
                        .eq(MarketingFormFieldEntity::getTenantId, tenantId)
                        .eq(MarketingFormFieldEntity::getFormId, formId)
                        .eq(MarketingFormFieldEntity::isDeleted, false));
        Collections.sort(fields, new Comparator<MarketingFormFieldEntity>() {
            @Override
            public int compare(MarketingFormFieldEntity first, MarketingFormFieldEntity second) {
                return safeInt(first.getSortOrder()).compareTo(safeInt(second.getSortOrder()));
            }
        });
        return fields;
    }

    private void replaceFields(Long tenantId, Long formId, List<MarketingFormFieldRequest> requests) {
        List<MarketingFormFieldEntity> oldFields = formFields(tenantId, formId);
        for (MarketingFormFieldEntity oldField : oldFields) {
            oldField.setDeleted(true);
            touch(oldField);
            marketingFormFieldMapper.updateById(oldField);
        }
        int index = 0;
        for (MarketingFormFieldRequest request : requests) {
            MarketingFormFieldEntity entity = new MarketingFormFieldEntity();
            prepareNew(entity, tenantId);
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setFormId(formId);
            entity.setFieldKey(resolveFieldKey(request, index));
            entity.setLabel(trimToNull(request.getLabel()));
            entity.setFieldType(request.getFieldType() == null ? MarketingFormFieldType.TEXT : request.getFieldType());
            entity.setRequiredField(request.isRequiredField());
            entity.setPlaceholder(trimToNull(request.getPlaceholder()));
            entity.setOptionsText(trimToNull(request.getOptionsText()));
            entity.setSystemMapping(resolveSystemMapping(request));
            entity.setSortOrder(request.getSortOrder() == null ? index : request.getSortOrder());
            marketingFormFieldMapper.insert(entity);
            index++;
        }
    }

    private List<MarketingFormFieldRequest> resolveFields(List<MarketingFormFieldRequest> fields) {
        if (fields == null || fields.isEmpty()) {
            return defaultFields();
        }
        List<MarketingFormFieldRequest> values = new ArrayList<MarketingFormFieldRequest>();
        int index = 0;
        for (MarketingFormFieldRequest field : fields) {
            if (field != null && StringUtils.hasText(field.getLabel())) {
                if (field.getSortOrder() == null) {
                    field.setSortOrder(index);
                }
                values.add(field);
                index++;
            }
        }
        return values.isEmpty() ? defaultFields() : values;
    }

    private List<MarketingFormFieldRequest> defaultFields() {
        List<MarketingFormFieldRequest> fields = new ArrayList<MarketingFormFieldRequest>();
        fields.add(defaultField("name", "姓名", MarketingFormFieldType.TEXT, false, "请填写姓名"));
        fields.add(defaultField("companyName", "公司名称", MarketingFormFieldType.TEXT, true, "请填写公司名称"));
        fields.add(defaultField("phone", "手机号", MarketingFormFieldType.PHONE, true, "请填写手机号"));
        fields.add(defaultField("email", "邮箱", MarketingFormFieldType.EMAIL, false, "请填写邮箱"));
        fields.add(defaultField("remark", "需求描述", MarketingFormFieldType.TEXTAREA, false, "请简单描述您的需求"));
        return fields;
    }

    private MarketingFormFieldRequest defaultField(
            String key, String label, MarketingFormFieldType type, boolean required, String placeholder) {
        MarketingFormFieldRequest field = new MarketingFormFieldRequest();
        field.setFieldKey(key);
        field.setLabel(label);
        field.setFieldType(type);
        field.setRequiredField(required);
        field.setPlaceholder(placeholder);
        field.setSystemMapping(key);
        field.setSortOrder(defaultOrder(key));
        return field;
    }

    private int defaultOrder(String key) {
        if ("name".equals(key)) {
            return 0;
        }
        if ("companyName".equals(key)) {
            return 1;
        }
        if ("phone".equals(key)) {
            return 2;
        }
        if ("email".equals(key)) {
            return 3;
        }
        return 4;
    }

    private void validateRequired(List<MarketingFormFieldEntity> fields, Map<String, String> values) {
        for (MarketingFormFieldEntity field : fields) {
            if (field.isRequiredField() && !StringUtils.hasText(values.get(field.getFieldKey()))) {
                throw new BusinessException("MARKETING_FORM_006", field.getLabel() + "不能为空");
            }
        }
    }

    private String mappedValue(List<MarketingFormFieldEntity> fields, Map<String, String> values, String mapping) {
        for (MarketingFormFieldEntity field : fields) {
            String fieldMapping = trimToNull(field.getSystemMapping());
            if (mapping.equals(fieldMapping) || mapping.equals(field.getFieldKey())) {
                return trimToNull(values.get(field.getFieldKey()));
            }
        }
        return trimToNull(values.get(mapping));
    }

    private String resolveFieldKey(MarketingFormFieldRequest request, int index) {
        String key = trimToNull(request.getFieldKey());
        if (key == null) {
            key = "field_" + index;
        }
        return key.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String resolveSystemMapping(MarketingFormFieldRequest request) {
        String mapping = trimToNull(request.getSystemMapping());
        if (mapping != null) {
            return mapping;
        }
        return trimToNull(request.getFieldKey());
    }

    private String resolveChannelTitle(MarketingFormEntity form, String contactName, String companyName) {
        String name = trimToNull(companyName);
        if (name == null) {
            name = trimToNull(contactName);
        }
        if (name == null) {
            name = form.getTitle();
        }
        return "表单提交-" + name;
    }

    private String resolveSource(MarketingFormEntity form) {
        String source = normalizeSource(form.getSource());
        return shrink("获客表单-" + source, 64);
    }

    private String resolveChannelRemark(MarketingFormEntity form, String remark) {
        StringBuilder builder = new StringBuilder();
        builder.append("来自获客表单：").append(form.getTitle());
        if (StringUtils.hasText(remark)) {
            builder.append("\n客户填写：").append(remark.trim());
        }
        return builder.toString();
    }

    private String resolveSubmitMessage(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return "提交成功，我们会尽快联系您。";
        }
        return text;
    }

    private MarketingFormResponse toResponse(MarketingFormEntity entity, boolean withFields) {
        MarketingFormResponse response = new MarketingFormResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setFormCode(entity.getFormCode());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setSource(entity.getSource());
        response.setSubmitMessage(entity.getSubmitMessage());
        response.setStatus(entity.getStatus());
        response.setAutoCreateLead(entity.isAutoCreateLead());
        response.setOwnerId(entity.getOwnerId());
        response.setViewCount(safeLong(entity.getViewCount()));
        response.setSubmitCount(safeLong(entity.getSubmitCount()));
        response.setPublicPath("/public/forms/" + entity.getFormCode());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        if (withFields) {
            response.setFields(fieldResponses(formFields(entity.getTenantId(), entity.getId())));
        }
        return response;
    }

    private List<MarketingFormFieldResponse> fieldResponses(List<MarketingFormFieldEntity> fields) {
        List<MarketingFormFieldResponse> records = new ArrayList<MarketingFormFieldResponse>();
        for (MarketingFormFieldEntity field : fields) {
            MarketingFormFieldResponse response = new MarketingFormFieldResponse();
            response.setId(field.getId());
            response.setFieldKey(field.getFieldKey());
            response.setLabel(field.getLabel());
            response.setFieldType(field.getFieldType());
            response.setRequiredField(field.isRequiredField());
            response.setPlaceholder(field.getPlaceholder());
            response.setOptionsText(field.getOptionsText());
            response.setSystemMapping(field.getSystemMapping());
            response.setSortOrder(field.getSortOrder());
            records.add(response);
        }
        return records;
    }

    private List<MarketingFormFieldResponse> publicFieldResponses(List<MarketingFormFieldEntity> fields) {
        List<MarketingFormFieldResponse> records = fieldResponses(fields);
        for (MarketingFormFieldResponse response : records) {
            response.setSystemMapping(null);
        }
        return records;
    }

    private String nextFormCode() {
        return "mf" + Long.toString(snowflakeIdGenerator.nextId(), 36);
    }

    private void checkDataScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_001", "无权访问该表单");
        }
    }

    private Long resolveOwnerId(Long operatorId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) || ownerId == null) {
            return operatorId;
        }
        return ownerId;
    }

    private void prepareNew(BaseEntity entity, Long tenantId) {
        LocalDateTime now = DateTimes.now();
        entity.setTenantId(tenantId);
        entity.setDeleted(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
    }

    private void touch(BaseEntity entity) {
        entity.setUpdatedAt(DateTimes.now());
    }

    private Long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private Integer safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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

    private String normalizeSource(String value) {
        return ChannelSource.from(value).name();
    }

    private String shrink(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
