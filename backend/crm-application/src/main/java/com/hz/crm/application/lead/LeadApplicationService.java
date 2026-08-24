package com.hz.crm.application.lead;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hz.crm.application.customer.CustomerApplicationService;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.application.customer.dto.CustomerSaveRequest;
import com.hz.crm.application.lead.dto.LeadAssignRequest;
import com.hz.crm.application.lead.dto.LeadConvertRequest;
import com.hz.crm.application.lead.dto.LeadConvertResponse;
import com.hz.crm.application.lead.dto.LeadImportError;
import com.hz.crm.application.lead.dto.LeadImportResult;
import com.hz.crm.application.lead.dto.LeadImportRow;
import com.hz.crm.application.lead.dto.LeadQuery;
import com.hz.crm.application.lead.dto.LeadResponse;
import com.hz.crm.application.lead.dto.LeadSaveRequest;
import com.hz.crm.application.product.ProductReferenceResolver;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.AssignableUserResolver;
import com.hz.crm.common.user.UserDataScopeValidator;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.CustomerLevel;
import com.hz.crm.domain.customer.CustomerStatus;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.LeadConvertType;
import com.hz.crm.domain.lead.LeadStatus;
import com.hz.crm.domain.lead.mapper.LeadMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadApplicationService {

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private CustomerApplicationService customerApplicationService;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired(required = false)
    private UserNameResolver userNameResolver;

    @Autowired
    private AssignableUserResolver assignableUserResolver;

    @Autowired
    private LeadMapper leadMapper;

    @Autowired
    private UserDataScopeValidator userDataScopeValidator;

    @Autowired
    private ProductReferenceResolver productReferenceResolver;

    @Transactional(readOnly = true)
    public PageData<LeadResponse> page(Long tenantId, Long userId, String dataScope, LeadQuery query) {
        LeadQuery safeQuery = query == null ? new LeadQuery() : query;
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        long offset = (long) (pageNo - 1) * pageSize;
        LambdaQueryWrapper<LeadEntity> wrapper = buildPageWrapper(
                tenantId, userId, dataScope, safeQuery);
        Long total = leadMapper.selectCount(wrapper);
        wrapper.orderByDesc(LeadEntity::getCreatedAt)
                .orderByDesc(LeadEntity::getId)
                .last("LIMIT " + pageSize + " OFFSET " + offset);
        List<LeadEntity> entities = leadMapper.selectList(wrapper);
        List<LeadResponse> records = new ArrayList<LeadResponse>();
        for (LeadEntity entity : entities) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        fillCustomerNames(tenantId, records);
        fillProductNames(tenantId, records);
        return PageData.of(total == null ? 0L : total.longValue(), pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public LeadResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        LeadEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        LeadResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillCustomerNames(tenantId, response);
        return response;
    }

    @Transactional
    public LeadResponse save(Long tenantId, Long operatorId, String dataScope, LeadSaveRequest request) {
        if (request == null) {
            throw new BusinessException("LEAD_003", "线索信息不能为空");
        }
        String leadName = trimToNull(request.getName());
        String companyName = trimToNull(request.getCompanyName());
        String phone = trimToNull(request.getPhone());
        if (companyName == null) {
            throw new BusinessException("LEAD_003", "公司名称不能为空");
        }
        if (leadName == null) {
            throw new BusinessException("LEAD_003", "联系人不能为空");
        }
        if (phone == null) {
            throw new BusinessException("LEAD_003", "联系电话不能为空");
        }
        LeadEntity entity;
        if (request.getId() == null) {
            entity = new LeadEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
        } else {
            entity = findOne(tenantId, request.getId());
            checkDataScope(operatorId, dataScope, entity.getOwnerId());
        }
        entity.setName(leadName);
        entity.setCompanyName(companyName);
        entity.setPhone(phone);
        entity.setEmail(trimToNull(request.getEmail()));
        entity.setSource(trimToNull(request.getSource()));
        entity.setStatus(request.getStatus() == null ? LeadStatus.recommended() : request.getStatus());
        Long targetOwnerId = request.getOwnerId() == null ? operatorId : request.getOwnerId();
        checkOwnerScope(operatorId, dataScope, targetOwnerId);
        entity.setOwnerId(targetOwnerId);
        if (request.getId() == null || request.getProductId() != null) {
            entity.setProductId(request.getProductId());
        }
        entity.setRemark(trimToNull(request.getRemark()));
        saveEntity(entity, request.getId() == null);
        LeadResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillCustomerNames(tenantId, response);
        return response;
    }

    @Transactional
    public LeadResponse createFromPublicPool(
            Long tenantId, Long operatorId, String dataScope, LeadSaveRequest request) {
        if (request == null) {
            throw new BusinessException("LEAD_PUBLIC_POOL_001", "公海线索信息不能为空");
        }
        String leadName = trimToNull(request.getName());
        if (leadName == null) {
            leadName = trimToNull(request.getCompanyName());
        }
        if (leadName == null) {
            throw new BusinessException("LEAD_PUBLIC_POOL_002", "公海数据缺少可识别的线索名称");
        }
        LeadEntity entity = new LeadEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(tenantId);
        entity.setName(leadName);
        entity.setCompanyName(trimToNull(request.getCompanyName()));
        entity.setPhone(trimToNull(request.getPhone()));
        entity.setEmail(trimToNull(request.getEmail()));
        entity.setSource(trimToNull(request.getSource()));
        entity.setStatus(request.getStatus() == null ? LeadStatus.recommended() : request.getStatus());
        Long targetOwnerId = request.getOwnerId() == null ? operatorId : request.getOwnerId();
        checkOwnerScope(operatorId, dataScope, targetOwnerId);
        entity.setOwnerId(targetOwnerId);
        entity.setProductId(request.getProductId());
        entity.setRemark(trimToNull(request.getRemark()));
        saveEntity(entity, true);
        LeadResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillCustomerNames(tenantId, response);
        return response;
    }

    @Transactional
    public LeadResponse assign(
            Long tenantId, Long operatorId, String dataScope, LeadAssignRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException("LEAD_ASSIGN_001", "线索编号不能为空");
        }
        if (request.getOwnerId() == null) {
            throw new BusinessException("LEAD_ASSIGN_002", "负责人不能为空");
        }
        LeadEntity entity = findOneForAssignment(tenantId, request.getId());
        userDataScopeValidator.checkOwnerAccess(tenantId, operatorId, dataScope, entity.getOwnerId());
        checkOwnerScope(operatorId, dataScope, request.getOwnerId());
        String ownerName = assignableUserResolver.resolveAssignableName(
                tenantId, operatorId, dataScope, request.getOwnerId());
        if (!request.getOwnerId().equals(entity.getOwnerId())) {
            LocalDateTime updatedAt = DateTimes.now();
            int updated = leadMapper.update(null, Wrappers.<LeadEntity>lambdaUpdate()
                    .eq(LeadEntity::getId, entity.getId())
                    .eq(LeadEntity::getTenantId, tenantId)
                    .eq(LeadEntity::isDeleted, false)
                    .set(LeadEntity::getOwnerId, request.getOwnerId())
                    .set(LeadEntity::getUpdatedAt, updatedAt));
            if (updated != 1) {
                throw new BusinessException("LEAD_ASSIGN_004", "线索分配失败，请刷新后重试");
            }
            entity.setOwnerId(request.getOwnerId());
            entity.setUpdatedAt(updatedAt);
        }
        LeadResponse response = toResponse(entity);
        response.setOwnerName(ownerName);
        fillCustomerNames(tenantId, response);
        return response;
    }

    @Transactional
    public LeadImportResult importRows(Long tenantId, Long operatorId, List<LeadImportRow> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("LEAD_IMPORT_011", "没有可导入的线索数据");
        }
        LeadImportResult result = new LeadImportResult();
        result.setTotalCount(rows.size());
        Set<String> phones = new HashSet<String>();
        Set<String> emails = new HashSet<String>();
        List<LeadEntity> existingLeads = leadMapper.selectList(Wrappers.<LeadEntity>lambdaQuery()
                .select(LeadEntity::getPhone, LeadEntity::getEmail)
                .eq(LeadEntity::getTenantId, tenantId)
                .eq(LeadEntity::isDeleted, false));
        for (LeadEntity existingLead : existingLeads) {
            String phone = normalizePhone(existingLead.getPhone());
            String email = normalizeEmail(existingLead.getEmail());
            if (phone != null) {
                phones.add(phone);
            }
            if (email != null) {
                emails.add(email);
            }
        }
        for (LeadImportRow row : rows) {
            importRow(tenantId, operatorId, row, phones, emails, result);
        }
        return result;
    }

    @Transactional
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        LeadEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        entity.setUpdatedAt(DateTimes.now());
        updateExistingEntity(entity);
    }

    @Transactional
    public LeadResponse saveAiAnalysis(
            Long tenantId,
            Long userId,
            String dataScope,
            Long leadId,
            String summary,
            String suggestedCustomerName,
            String suggestedContactName,
            BigDecimal confidence) {
        LeadEntity entity = findOne(tenantId, leadId);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setAiSummary(trimToNull(summary));
        entity.setAiSuggestedCustomerName(trimToNull(suggestedCustomerName));
        entity.setAiSuggestedContactName(trimToNull(suggestedContactName));
        entity.setAiConfidence(confidence);
        entity.setAiAnalyzedAt(DateTimes.now());
        entity.setUpdatedAt(DateTimes.now());
        updateExistingEntity(entity);
        LeadResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        fillCustomerNames(tenantId, response);
        return response;
    }

    @Transactional
    public LeadConvertResponse convertToCustomer(
            Long tenantId, Long operatorId, String dataScope, LeadConvertRequest request) {
        if (request == null || request.getLeadId() == null) {
            throw new BusinessException("LEAD_CONVERT_001", "线索编号不能为空");
        }
        LeadEntity entity = findOne(tenantId, request.getLeadId());
        checkDataScope(operatorId, dataScope, entity.getOwnerId());
        if (entity.getCustomerId() != null || LeadStatus.CONVERTED == entity.getStatus()) {
            throw new BusinessException("LEAD_CONVERT_002", "线索已转为客户");
        }
        LeadConvertType convertType = request.getConvertType() == null
                ? LeadConvertType.CREATE_CUSTOMER
                : request.getConvertType();
        CustomerResponse customer;
        if (LeadConvertType.BIND_CUSTOMER == convertType) {
            customer = bindCustomer(tenantId, operatorId, dataScope, request);
        } else {
            customer = createCustomer(tenantId, operatorId, dataScope, entity, request);
        }
        entity.setCustomerId(customer.getId());
        entity.setStatus(LeadStatus.CONVERTED);
        entity.setConvertedAt(DateTimes.now());
        entity.setConvertedBy(operatorId);
        entity.setConvertedType(convertType);
        entity.setUpdatedAt(DateTimes.now());
        updateExistingEntity(entity);
        LeadResponse lead = toResponse(entity);
        fillOwnerName(tenantId, lead);
        fillCustomerNames(tenantId, lead);
        LeadConvertResponse response = new LeadConvertResponse();
        response.setLead(lead);
        response.setCustomer(customer);
        return response;
    }

    private CustomerResponse bindCustomer(
            Long tenantId, Long operatorId, String dataScope, LeadConvertRequest request) {
        if (request.getCustomerId() == null) {
            throw new BusinessException("LEAD_CONVERT_003", "请选择要绑定的客户");
        }
        return customerApplicationService.detail(tenantId, operatorId, dataScope, request.getCustomerId());
    }

    private CustomerResponse createCustomer(
            Long tenantId, Long operatorId, String dataScope, LeadEntity entity, LeadConvertRequest request) {
        CustomerSaveRequest customerRequest = new CustomerSaveRequest();
        customerRequest.setName(resolveCustomerName(entity, request));
        customerRequest.setIndustry(trimToNull(request.getIndustry()));
        customerRequest.setContactName(resolveContactName(entity, request));
        customerRequest.setContactPhone(resolveText(request.getContactPhone(), entity.getPhone()));
        customerRequest.setContactEmail(resolveText(request.getContactEmail(), entity.getEmail()));
        customerRequest.setLevel(request.getLevel() == null ? CustomerLevel.NORMAL : request.getLevel());
        customerRequest.setStatus(request.getStatus() == null ? CustomerStatus.recommended() : request.getStatus());
        customerRequest.setOwnerId(request.getOwnerId() == null ? entity.getOwnerId() : request.getOwnerId());
        customerRequest.setRemark(resolveCustomerRemark(entity, request));
        return customerApplicationService.save(tenantId, operatorId, dataScope, customerRequest);
    }

    private String resolveCustomerName(LeadEntity entity, LeadConvertRequest request) {
        String name = trimToNull(request.getCustomerName());
        if (name != null) {
            return name;
        }
        name = trimToNull(entity.getCompanyName());
        if (name != null) {
            return name;
        }
        return entity.getName();
    }

    private String resolveContactName(LeadEntity entity, LeadConvertRequest request) {
        String name = trimToNull(request.getContactName());
        if (name != null) {
            return name;
        }
        return entity.getName();
    }

    private String resolveText(String first, String second) {
        String value = trimToNull(first);
        if (value != null) {
            return value;
        }
        return trimToNull(second);
    }

    private String resolveCustomerRemark(LeadEntity entity, LeadConvertRequest request) {
        String remark = trimToNull(request.getRemark());
        if (remark != null) {
            return remark;
        }
        return trimToNull(entity.getRemark());
    }

    private LambdaQueryWrapper<LeadEntity> buildPageWrapper(
            Long tenantId,
            Long userId,
            String dataScope,
            LeadQuery query) {
        LambdaQueryWrapper<LeadEntity> wrapper = Wrappers.<LeadEntity>lambdaQuery()
                .eq(LeadEntity::getTenantId, tenantId)
                .eq(LeadEntity::isDeleted, false);
        if ("SELF".equals(dataScope)) {
            wrapper.eq(LeadEntity::getOwnerId, userId);
        }
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(item -> item
                    .like(LeadEntity::getName, keyword)
                    .or()
                    .like(LeadEntity::getCompanyName, keyword)
                    .or()
                    .like(LeadEntity::getPhone, keyword)
                    .or()
                    .like(LeadEntity::getEmail, keyword)
                    .or()
                    .like(LeadEntity::getSource, keyword));
        }
        if (query.getStatus() != null) {
            wrapper.eq(LeadEntity::getStatus, query.getStatus());
        }
        return wrapper;
    }

    private void saveEntity(LeadEntity entity, boolean newRecord) {
        LocalDateTime now = DateTimes.now();
        entity.setUpdatedAt(now);
        if (newRecord) {
            entity.setDeleted(false);
            entity.setCreatedAt(now);
            int inserted = leadMapper.insert(entity);
            if (inserted != 1) {
                throw new BusinessException("LEAD_004", "线索保存失败");
            }
            return;
        }
        updateExistingEntity(entity);
    }

    private void updateExistingEntity(LeadEntity entity) {
        int updated = leadMapper.update(null, Wrappers.<LeadEntity>lambdaUpdate()
                .eq(LeadEntity::getId, entity.getId())
                .eq(LeadEntity::getTenantId, entity.getTenantId())
                .eq(LeadEntity::isDeleted, false)
                .set(LeadEntity::getName, entity.getName())
                .set(LeadEntity::getCompanyName, entity.getCompanyName())
                .set(LeadEntity::getPhone, entity.getPhone())
                .set(LeadEntity::getEmail, entity.getEmail())
                .set(LeadEntity::getSource, entity.getSource())
                .set(LeadEntity::getStatus, entity.getStatus())
                .set(LeadEntity::getCustomerId, entity.getCustomerId())
                .set(LeadEntity::getConvertedAt, entity.getConvertedAt())
                .set(LeadEntity::getConvertedBy, entity.getConvertedBy())
                .set(LeadEntity::getConvertedType, entity.getConvertedType())
                .set(LeadEntity::getAiSummary, entity.getAiSummary())
                .set(LeadEntity::getAiSuggestedCustomerName, entity.getAiSuggestedCustomerName())
                .set(LeadEntity::getAiSuggestedContactName, entity.getAiSuggestedContactName())
                .set(LeadEntity::getAiConfidence, entity.getAiConfidence())
                .set(LeadEntity::getAiAnalyzedAt, entity.getAiAnalyzedAt())
                .set(LeadEntity::getOwnerId, entity.getOwnerId())
                .set(LeadEntity::getProductId, entity.getProductId())
                .set(LeadEntity::getRemark, entity.getRemark())
                .set(LeadEntity::isDeleted, entity.isDeleted())
                .set(LeadEntity::getUpdatedAt, entity.getUpdatedAt()));
        if (updated != 1) {
            throw new BusinessException("LEAD_004", "线索保存失败，请刷新后重试");
        }
    }

    private void importRow(
            Long tenantId,
            Long operatorId,
            LeadImportRow row,
            Set<String> phones,
            Set<String> emails,
            LeadImportResult result) {
        String name = trimToNull(row.getName());
        String companyName = trimToNull(row.getCompanyName());
        String phone = trimToNull(row.getPhone());
        String email = trimToNull(row.getEmail());
        if (name == null && companyName == null) {
            addImportError(result, row, "FAILED", "名称和公司名称至少填写一个");
            result.setFailedCount(result.getFailedCount() + 1);
            return;
        }
        String fieldError = validateImportFields(name, companyName, phone, email);
        if (fieldError != null) {
            addImportError(result, row, "FAILED", fieldError);
            result.setFailedCount(result.getFailedCount() + 1);
            return;
        }
        String normalizedPhone = normalizePhone(phone);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedPhone != null && phones.contains(normalizedPhone)) {
            addImportError(result, row, "SKIPPED", "联系电话已存在");
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }
        if (normalizedEmail != null && emails.contains(normalizedEmail)) {
            addImportError(result, row, "SKIPPED", "联系邮箱已存在");
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }
        LocalDateTime now = DateTimes.now();
        LeadEntity entity = new LeadEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(tenantId);
        entity.setName(name == null ? companyName : name);
        entity.setCompanyName(companyName);
        entity.setPhone(phone);
        entity.setEmail(email);
        entity.setSource(resolveImportSource(row.getSource()));
        entity.setStatus(LeadStatus.recommended());
        entity.setOwnerId(operatorId);
        entity.setRemark(buildImportRemark(row));
        entity.setDeleted(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        int inserted = leadMapper.insert(entity);
        if (inserted != 1) {
            throw new BusinessException("LEAD_IMPORT_012", "Excel线索导入失败");
        }
        if (normalizedPhone != null) {
            phones.add(normalizedPhone);
        }
        if (normalizedEmail != null) {
            emails.add(normalizedEmail);
        }
        result.setImportedCount(result.getImportedCount() + 1);
    }

    private String validateImportFields(String name, String companyName, String phone, String email) {
        if (name != null && name.length() > 128) {
            return "名称不能超过128个字符";
        }
        if (companyName != null && companyName.length() > 128) {
            return "公司名称不能超过128个字符";
        }
        if (phone != null && phone.length() > 32) {
            return "联系电话不能超过32个字符";
        }
        if (email != null && email.length() > 128) {
            return "联系邮箱不能超过128个字符";
        }
        return null;
    }

    private void addImportError(LeadImportResult result, LeadImportRow row, String type, String reason) {
        LeadImportError error = new LeadImportError();
        error.setRowNumber(row.getRowNumber());
        error.setName(trimToNull(row.getName()));
        error.setCompanyName(trimToNull(row.getCompanyName()));
        error.setType(type);
        error.setReason(reason);
        result.getErrors().add(error);
    }

    private String resolveImportSource(String value) {
        String source = trimToNull(value);
        if (source == null) {
            return "OTHER";
        }
        String upperSource = source.toUpperCase(Locale.ROOT);
        List<String> sourceCodes = Arrays.asList(
                "WEBSITE",
                "LANDING_PAGE",
                "SMS",
                "WECHAT",
                "WECHAT_GROUP",
                "PHONE",
                "OFFLINE_EVENT",
                "LIVE",
                "REFERRAL",
                "AD",
                "OTHER");
        if (sourceCodes.contains(upperSource)) {
            return upperSource;
        }
        if (source.contains("微信群")) {
            return "WECHAT_GROUP";
        }
        if (source.contains("微信")) {
            return "WECHAT";
        }
        if (source.contains("电话")) {
            return "PHONE";
        }
        if (source.contains("官网") || source.contains("网站")) {
            return "WEBSITE";
        }
        if (source.contains("落地页") || source.contains("表单")) {
            return "LANDING_PAGE";
        }
        if (source.contains("短信")) {
            return "SMS";
        }
        if (source.contains("线下") || source.contains("活动")) {
            return "OFFLINE_EVENT";
        }
        if (source.contains("直播")) {
            return "LIVE";
        }
        if (source.contains("推荐") || source.contains("转介绍")) {
            return "REFERRAL";
        }
        if (source.contains("广告")) {
            return "AD";
        }
        return "OTHER";
    }

    private String buildImportRemark(LeadImportRow row) {
        Map<String, String> fields = row.getAdditionalFields() == null
                ? new LinkedHashMap<String, String>()
                : row.getAdditionalFields();
        if (fields.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("## Excel 导入信息");
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = trimToNull(entry.getKey());
            String value = trimToNull(entry.getValue());
            if (key == null || value == null) {
                continue;
            }
            builder.append("\n\n- **")
                    .append(key)
                    .append("**：")
                    .append(value.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\n  "));
        }
        return builder.length() == "## Excel 导入信息".length() ? null : builder.toString();
    }

    private String normalizePhone(String value) {
        String phone = trimToNull(value);
        if (phone == null) {
            return null;
        }
        return phone.replace(" ", "").replace("-", "");
    }

    private String normalizeEmail(String value) {
        String email = trimToNull(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private LeadEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("LEAD_001", "线索编号不能为空");
        }
        LeadEntity entity = leadMapper.selectOne(Wrappers.<LeadEntity>lambdaQuery()
                .eq(LeadEntity::getId, id)
                .eq(LeadEntity::getTenantId, tenantId)
                .eq(LeadEntity::isDeleted, false));
        if (entity == null) {
            throw new BusinessException("LEAD_002", "线索不存在");
        }
        return entity;
    }

    private LeadEntity findOneForAssignment(Long tenantId, Long id) {
        LeadEntity entity = leadMapper.selectOne(Wrappers.<LeadEntity>lambdaQuery()
                .eq(LeadEntity::getId, id)
                .eq(LeadEntity::getTenantId, tenantId)
                .eq(LeadEntity::isDeleted, false));
        if (entity == null) {
            throw new BusinessException("LEAD_ASSIGN_003", "线索不存在");
        }
        return entity;
    }

    private void checkDataScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_001", "无权访问该数据");
        }
    }

    private void checkOwnerScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_002", "本人数据权限不能分配给其他负责人");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }

    private LeadResponse toResponse(LeadEntity entity) {
        LeadResponse response = new LeadResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setName(entity.getName());
        response.setCompanyName(entity.getCompanyName());
        response.setPhone(entity.getPhone());
        response.setEmail(entity.getEmail());
        response.setSource(entity.getSource());
        response.setStatus(entity.getStatus());
        response.setCustomerId(entity.getCustomerId());
        response.setConvertedAt(entity.getConvertedAt());
        response.setConvertedBy(entity.getConvertedBy());
        response.setConvertedType(entity.getConvertedType());
        response.setAiSummary(entity.getAiSummary());
        response.setAiSuggestedCustomerName(entity.getAiSuggestedCustomerName());
        response.setAiSuggestedContactName(entity.getAiSuggestedContactName());
        response.setAiConfidence(entity.getAiConfidence());
        response.setAiAnalyzedAt(entity.getAiAnalyzedAt());
        response.setOwnerId(entity.getOwnerId());
        response.setProductId(entity.getProductId());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private void fillOwnerName(Long tenantId, LeadResponse response) {
        List<LeadResponse> records = new ArrayList<LeadResponse>();
        records.add(response);
        fillOwnerNames(tenantId, records);
        fillProductNames(tenantId, records);
    }

    private void fillCustomerNames(Long tenantId, LeadResponse response) {
        List<LeadResponse> records = new ArrayList<LeadResponse>();
        records.add(response);
        fillCustomerNames(tenantId, records);
    }

    private void fillOwnerNames(Long tenantId, List<LeadResponse> records) {
        if (userNameResolver == null || records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ownerIds = new HashSet<Long>();
        for (LeadResponse response : records) {
            if (response.getOwnerId() != null) {
                ownerIds.add(response.getOwnerId());
            }
            if (response.getConvertedBy() != null) {
                ownerIds.add(response.getConvertedBy());
            }
        }
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userNameResolver.resolve(tenantId, ownerIds);
        for (LeadResponse response : records) {
            if (response.getOwnerId() != null) {
                response.setOwnerName(names.get(response.getOwnerId()));
            }
            if (response.getConvertedBy() != null) {
                response.setConvertedByName(names.get(response.getConvertedBy()));
            }
        }
    }

    private void fillCustomerNames(Long tenantId, List<LeadResponse> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> customerIds = new HashSet<Long>();
        for (LeadResponse response : records) {
            if (response.getCustomerId() != null) {
                customerIds.add(response.getCustomerId());
            }
        }
        if (customerIds.isEmpty()) {
            return;
        }
        List<CustomerEntity> customers = customerMapper.selectList(Wrappers.<CustomerEntity>lambdaQuery()
                .eq(CustomerEntity::getTenantId, tenantId)
                .eq(CustomerEntity::isDeleted, false)
                .in(CustomerEntity::getId, customerIds));
        Map<Long, String> names = new HashMap<Long, String>();
        for (CustomerEntity customer : customers) {
            names.put(customer.getId(), customer.getName());
        }
        for (LeadResponse response : records) {
            if (response.getCustomerId() != null) {
                response.setCustomerName(names.get(response.getCustomerId()));
            }
        }
    }

    private void fillProductNames(Long tenantId, List<LeadResponse> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> productIds = new HashSet<Long>();
        for (LeadResponse response : records) {
            if (response.getProductId() != null) {
                productIds.add(response.getProductId());
            }
        }
        Map<Long, String> names = productReferenceResolver.resolveNames(tenantId, productIds);
        for (LeadResponse response : records) {
            if (response.getProductId() != null) {
                response.setProductName(names.get(response.getProductId()));
            }
        }
    }
}
