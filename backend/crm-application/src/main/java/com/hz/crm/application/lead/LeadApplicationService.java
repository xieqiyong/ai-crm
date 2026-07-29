package com.hz.crm.application.lead;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.application.customer.CustomerApplicationService;
import com.hz.crm.application.customer.dto.CustomerResponse;
import com.hz.crm.application.customer.dto.CustomerSaveRequest;
import com.hz.crm.application.lead.dto.LeadAssignRequest;
import com.hz.crm.application.lead.dto.LeadConvertRequest;
import com.hz.crm.application.lead.dto.LeadConvertResponse;
import com.hz.crm.application.lead.dto.LeadQuery;
import com.hz.crm.application.lead.dto.LeadResponse;
import com.hz.crm.application.lead.dto.LeadSaveRequest;
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
import com.hz.crm.domain.lead.repository.LeadJpaRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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

@Service
public class LeadApplicationService {

    @Autowired
    private LeadJpaRepository leadRepository;

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

    @Transactional(readOnly = true)
    public PageData<LeadResponse> page(Long tenantId, Long userId, String dataScope, LeadQuery query) {
        LeadQuery safeQuery = query == null ? new LeadQuery() : query;
        PageRequest pageRequest = PageRequest.of(
                safeQuery.safePageNo() - 1, safeQuery.safePageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Long ownerId = "SELF".equals(dataScope) ? userId : null;
        Page<LeadEntity> page = leadRepository.search(
                tenantId, ownerId, likeKeyword(safeQuery.getKeyword()), safeQuery.getStatus(), pageRequest);
        List<LeadResponse> records = new ArrayList<LeadResponse>();
        for (LeadEntity entity : page.getContent()) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        fillCustomerNames(tenantId, records);
        return PageData.of(page.getTotalElements(), safeQuery.safePageNo(), safeQuery.safePageSize(), records);
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
            throw new BusinessException("LEAD_003", "名称和公司名称至少填写一个");
        }
        String leadName = trimToNull(request.getName());
        String companyName = trimToNull(request.getCompanyName());
        if (leadName == null && companyName == null) {
            throw new BusinessException("LEAD_003", "名称和公司名称至少填写一个");
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
        entity.setName(leadName == null ? companyName : leadName);
        entity.setCompanyName(companyName);
        entity.setPhone(trimToNull(request.getPhone()));
        entity.setEmail(trimToNull(request.getEmail()));
        entity.setSource(trimToNull(request.getSource()));
        entity.setStatus(request.getStatus() == null ? LeadStatus.recommended() : request.getStatus());
        Long targetOwnerId = request.getOwnerId() == null ? operatorId : request.getOwnerId();
        checkOwnerScope(operatorId, dataScope, targetOwnerId);
        entity.setOwnerId(targetOwnerId);
        entity.setRemark(trimToNull(request.getRemark()));
        LeadResponse response = toResponse(leadRepository.save(entity));
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
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        LeadEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        leadRepository.save(entity);
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
        LeadResponse response = toResponse(leadRepository.save(entity));
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
        LeadResponse lead = toResponse(leadRepository.save(entity));
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

    private LeadEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("LEAD_001", "线索编号不能为空");
        }
        return leadRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("LEAD_002", "线索不存在"));
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

    private String likeKeyword(String value) {
        String keyword = trimToNull(value);
        return keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
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
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private void fillOwnerName(Long tenantId, LeadResponse response) {
        List<LeadResponse> records = new ArrayList<LeadResponse>();
        records.add(response);
        fillOwnerNames(tenantId, records);
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
}
