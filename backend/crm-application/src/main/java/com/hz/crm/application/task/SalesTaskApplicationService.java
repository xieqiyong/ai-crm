package com.hz.crm.application.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.application.task.dto.SalesTaskAssignRequest;
import com.hz.crm.application.task.dto.SalesTaskQuery;
import com.hz.crm.application.task.dto.SalesTaskResponse;
import com.hz.crm.application.task.dto.SalesTaskSaveRequest;
import com.hz.crm.application.task.dto.SalesTaskStatusRequest;
import com.hz.crm.application.task.dto.SalesTaskTargetOptionQuery;
import com.hz.crm.application.task.dto.SalesTaskTargetOptionResponse;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.UserDataScopeValidator;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.mapper.ChannelRecordMapper;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.followup.FollowupRecordEntity;
import com.hz.crm.domain.followup.FollowupTargetType;
import com.hz.crm.domain.lead.LeadEntity;
import com.hz.crm.domain.lead.mapper.LeadMapper;
import com.hz.crm.domain.message.LocalMessageEntity;
import com.hz.crm.domain.message.mapper.LocalMessageMapper;
import com.hz.crm.domain.opportunity.OpportunityEntity;
import com.hz.crm.domain.opportunity.mapper.OpportunityMapper;
import com.hz.crm.domain.task.SalesTaskEntity;
import com.hz.crm.domain.task.SalesTaskPriority;
import com.hz.crm.domain.task.SalesTaskSource;
import com.hz.crm.domain.task.SalesTaskStatus;
import com.hz.crm.domain.task.SalesTaskTargetType;
import com.hz.crm.domain.task.mapper.SalesTaskMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SalesTaskApplicationService {

    private static final String TASK_REMINDER_MESSAGE_TYPE = "TASK_REMINDER";

    private static final String SALES_TASK_BUSINESS_TYPE = "SALES_TASK";

    private static final String MESSAGE_STATUS_PENDING = "PENDING";

    private static final String MESSAGE_STATUS_CANCELLED = "CANCELLED";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private SalesTaskMapper salesTaskMapper;

    @Autowired
    private LocalMessageMapper localMessageMapper;

    @Autowired
    private LeadMapper leadMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private OpportunityMapper opportunityMapper;

    @Autowired
    private ChannelRecordMapper channelRecordMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired(required = false)
    private UserNameResolver userNameResolver;

    @Autowired(required = false)
    private UserDataScopeValidator userDataScopeValidator;

    @Transactional(readOnly = true)
    public PageData<SalesTaskResponse> page(Long tenantId, Long userId, String dataScope, SalesTaskQuery query) {
        SalesTaskQuery safeQuery = query == null ? new SalesTaskQuery() : query;
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        int offset = (pageNo - 1) * pageSize;
        QueryWrapper<SalesTaskEntity> countWrapper = buildPageWrapper(tenantId, userId, dataScope, safeQuery);
        Long total = salesTaskMapper.selectCount(countWrapper);
        QueryWrapper<SalesTaskEntity> wrapper = buildPageWrapper(tenantId, userId, dataScope, safeQuery);
        wrapper.orderByAsc("due_at")
                .orderByDesc("created_at")
                .last("limit " + pageSize + " offset " + offset);
        List<SalesTaskEntity> entities = salesTaskMapper.selectList(wrapper);
        List<SalesTaskResponse> records = new ArrayList<SalesTaskResponse>();
        for (SalesTaskEntity entity : entities) {
            records.add(toResponse(entity));
        }
        fillUserNames(tenantId, records);
        return PageData.of(total == null ? 0L : total.longValue(), pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public SalesTaskResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        SalesTaskEntity entity = findOne(tenantId, id);
        checkOwnerAccess(tenantId, userId, dataScope, entity.getOwnerId());
        SalesTaskResponse response = toResponse(entity);
        fillUserName(tenantId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<SalesTaskTargetOptionResponse> targetOptions(
            Long tenantId, Long userId, String dataScope, SalesTaskTargetOptionQuery query) {
        SalesTaskTargetOptionQuery safeQuery = query == null ? new SalesTaskTargetOptionQuery() : query;
        if (safeQuery.getTargetType() == null || SalesTaskTargetType.GENERAL == safeQuery.getTargetType()) {
            return new ArrayList<SalesTaskTargetOptionResponse>();
        }
        if (SalesTaskTargetType.LEAD == safeQuery.getTargetType()) {
            return queryLeadTargetOptions(tenantId, userId, dataScope, safeQuery);
        }
        if (SalesTaskTargetType.CUSTOMER == safeQuery.getTargetType()) {
            return queryCustomerTargetOptions(tenantId, userId, dataScope, safeQuery);
        }
        if (SalesTaskTargetType.OPPORTUNITY == safeQuery.getTargetType()) {
            return queryOpportunityTargetOptions(tenantId, userId, dataScope, safeQuery);
        }
        if (SalesTaskTargetType.CHANNEL == safeQuery.getTargetType()) {
            return queryChannelTargetOptions(tenantId, userId, dataScope, safeQuery);
        }
        return new ArrayList<SalesTaskTargetOptionResponse>();
    }

    @Transactional
    public SalesTaskResponse save(Long tenantId, Long operatorId, String dataScope, SalesTaskSaveRequest request) {
        validateSaveRequest(request);
        LocalDateTime now = DateTimes.now();
        SalesTaskEntity entity;
        boolean creating = request.getId() == null;
        if (creating) {
            entity = new SalesTaskEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setDeleted(false);
            entity.setCreatedAt(now);
            entity.setCreatorId(operatorId);
            entity.setSource(SalesTaskSource.MANUAL);
            entity.setStatus(SalesTaskStatus.PENDING);
        } else {
            entity = findOne(tenantId, request.getId());
            checkOwnerAccess(tenantId, operatorId, dataScope, entity.getOwnerId());
            if (SalesTaskStatus.COMPLETED == entity.getStatus()) {
                throw new BusinessException("TASK_009", "已完成任务不能编辑");
            }
            if (SalesTaskStatus.CANCELLED == entity.getStatus()) {
                throw new BusinessException("TASK_010", "已取消任务不能编辑");
            }
        }
        Long ownerId = request.getOwnerId() == null ? operatorId : request.getOwnerId();
        checkOwnerAccess(tenantId, operatorId, dataScope, ownerId);
        entity.setTitle(trimToNull(request.getTitle()));
        entity.setContent(trimToNull(request.getContent()));
        entity.setTargetType(request.getTargetType() == null ? SalesTaskTargetType.GENERAL : request.getTargetType());
        entity.setTargetId(request.getTargetId());
        entity.setTargetName(trimToNull(request.getTargetName()));
        entity.setOwnerId(ownerId);
        entity.setDueAt(request.getDueAt());
        entity.setReminderAt(request.getReminderAt());
        entity.setPriority(request.getPriority() == null ? SalesTaskPriority.MEDIUM : request.getPriority());
        entity.setUpdatedAt(now);
        int affected = creating ? salesTaskMapper.insert(entity) : salesTaskMapper.updateById(entity);
        if (affected != 1) {
            throw new BusinessException("TASK_011", "任务保存失败，请刷新后重试");
        }
        syncReminderMessage(entity);
        SalesTaskResponse response = toResponse(entity);
        fillUserName(tenantId, response);
        return response;
    }

    @Transactional
    public SalesTaskResponse start(Long tenantId, Long operatorId, String dataScope, Long id) {
        SalesTaskEntity entity = findOne(tenantId, id);
        checkOwnerAccess(tenantId, operatorId, dataScope, entity.getOwnerId());
        if (SalesTaskStatus.COMPLETED == entity.getStatus()) {
            throw new BusinessException("TASK_012", "已完成任务不能开始");
        }
        if (SalesTaskStatus.CANCELLED == entity.getStatus()) {
            throw new BusinessException("TASK_013", "已取消任务不能开始");
        }
        entity.setStatus(SalesTaskStatus.IN_PROGRESS);
        entity.setUpdatedAt(DateTimes.now());
        salesTaskMapper.updateById(entity);
        syncReminderMessage(entity);
        SalesTaskResponse response = toResponse(entity);
        fillUserName(tenantId, response);
        return response;
    }

    @Transactional
    public SalesTaskResponse complete(Long tenantId, Long operatorId, String dataScope, Long id) {
        SalesTaskEntity entity = findOne(tenantId, id);
        checkOwnerAccess(tenantId, operatorId, dataScope, entity.getOwnerId());
        if (SalesTaskStatus.CANCELLED == entity.getStatus()) {
            throw new BusinessException("TASK_014", "已取消任务不能完成");
        }
        LocalDateTime now = DateTimes.now();
        entity.setStatus(SalesTaskStatus.COMPLETED);
        entity.setCompletedAt(now);
        entity.setCompletedBy(operatorId);
        entity.setUpdatedAt(now);
        salesTaskMapper.updateById(entity);
        syncReminderMessage(entity);
        SalesTaskResponse response = toResponse(entity);
        fillUserName(tenantId, response);
        return response;
    }

    @Transactional
    public SalesTaskResponse cancel(
            Long tenantId, Long operatorId, String dataScope, SalesTaskStatusRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException("TASK_001", "任务编号不能为空");
        }
        SalesTaskEntity entity = findOne(tenantId, request.getId());
        checkOwnerAccess(tenantId, operatorId, dataScope, entity.getOwnerId());
        if (SalesTaskStatus.COMPLETED == entity.getStatus()) {
            throw new BusinessException("TASK_015", "已完成任务不能取消");
        }
        entity.setStatus(SalesTaskStatus.CANCELLED);
        entity.setCancelReason(trimToNull(request.getCancelReason()));
        entity.setUpdatedAt(DateTimes.now());
        salesTaskMapper.updateById(entity);
        syncReminderMessage(entity);
        SalesTaskResponse response = toResponse(entity);
        fillUserName(tenantId, response);
        return response;
    }

    @Transactional
    public SalesTaskResponse assign(
            Long tenantId, Long operatorId, String dataScope, SalesTaskAssignRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException("TASK_ASSIGN_001", "任务编号不能为空");
        }
        if (request.getOwnerId() == null) {
            throw new BusinessException("TASK_ASSIGN_002", "负责人不能为空");
        }
        SalesTaskEntity entity = findOne(tenantId, request.getId());
        checkOwnerAccess(tenantId, operatorId, dataScope, entity.getOwnerId());
        checkOwnerAccess(tenantId, operatorId, dataScope, request.getOwnerId());
        if (SalesTaskStatus.COMPLETED == entity.getStatus()) {
            throw new BusinessException("TASK_ASSIGN_003", "已完成任务不能分配");
        }
        if (SalesTaskStatus.CANCELLED == entity.getStatus()) {
            throw new BusinessException("TASK_ASSIGN_004", "已取消任务不能分配");
        }
        if (!request.getOwnerId().equals(entity.getOwnerId())) {
            entity.setOwnerId(request.getOwnerId());
            entity.setUpdatedAt(DateTimes.now());
            salesTaskMapper.updateById(entity);
            syncReminderMessage(entity);
        }
        SalesTaskResponse response = toResponse(entity);
        fillUserName(tenantId, response);
        return response;
    }

    @Transactional
    public void delete(Long tenantId, Long operatorId, String dataScope, Long id) {
        SalesTaskEntity entity = findOne(tenantId, id);
        checkOwnerAccess(tenantId, operatorId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        entity.setUpdatedAt(DateTimes.now());
        salesTaskMapper.updateById(entity);
        syncReminderMessage(entity);
    }

    @Transactional
    public void syncFollowupTask(Long tenantId, Long operatorId, String dataScope, FollowupRecordEntity followup) {
        if (followup == null || followup.getId() == null) {
            return;
        }
        SalesTaskEntity entity = findFollowupTask(tenantId, followup.getId());
        if (followup.getNextFollowTime() == null) {
            cancelPendingFollowupTask(entity);
            return;
        }
        SalesTaskTargetType targetType = convertTargetType(followup.getTargetType());
        if (targetType == null) {
            return;
        }
        LocalDateTime now = DateTimes.now();
        if (entity == null) {
            entity = new SalesTaskEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setDeleted(false);
            entity.setCreatedAt(now);
            entity.setCreatorId(operatorId);
            entity.setSource(SalesTaskSource.FOLLOWUP);
            entity.setSourceId(followup.getId());
            entity.setStatus(SalesTaskStatus.PENDING);
        }
        if (SalesTaskStatus.COMPLETED == entity.getStatus() || SalesTaskStatus.CANCELLED == entity.getStatus()) {
            return;
        }
        Long ownerId = followup.getOwnerId() == null ? operatorId : followup.getOwnerId();
        checkOwnerAccess(tenantId, operatorId, dataScope, ownerId);
        entity.setTitle(buildFollowupTaskTitle(followup));
        entity.setContent(trimToNull(followup.getNextPlan()));
        entity.setTargetType(targetType);
        entity.setTargetId(followup.getTargetId());
        entity.setTargetName(trimToNull(followup.getTargetName()));
        entity.setOwnerId(ownerId);
        entity.setDueAt(followup.getNextFollowTime());
        entity.setReminderAt(resolveReminderTime(followup.getNextFollowTime()));
        entity.setPriority(SalesTaskPriority.MEDIUM);
        entity.setUpdatedAt(now);
        if (entity.getId() == null) {
            return;
        }
        if (findFollowupTask(tenantId, followup.getId()) == null) {
            salesTaskMapper.insert(entity);
        } else {
            salesTaskMapper.updateById(entity);
        }
        syncReminderMessage(entity);
    }

    private void cancelPendingFollowupTask(SalesTaskEntity entity) {
        if (entity == null) {
            return;
        }
        if (SalesTaskStatus.COMPLETED == entity.getStatus() || SalesTaskStatus.CANCELLED == entity.getStatus()) {
            return;
        }
        entity.setStatus(SalesTaskStatus.CANCELLED);
        entity.setCancelReason("跟进记录已取消下次跟进");
        entity.setUpdatedAt(DateTimes.now());
        salesTaskMapper.updateById(entity);
        syncReminderMessage(entity);
    }

    private void syncReminderMessage(SalesTaskEntity task) {
        if (task == null || task.getId() == null || task.getTenantId() == null) {
            return;
        }
        LocalMessageEntity message = findTaskReminderMessage(task.getTenantId(), task.getId());
        if (!shouldCreateReminderMessage(task)) {
            cancelReminderMessage(message);
            return;
        }
        LocalDateTime now = DateTimes.now();
        boolean creating = message == null;
        if (creating) {
            message = new LocalMessageEntity();
            message.setId(snowflakeIdGenerator.nextId());
            message.setTenantId(task.getTenantId());
            message.setDeleted(false);
            message.setCreatedAt(now);
            message.setMessageType(TASK_REMINDER_MESSAGE_TYPE);
            message.setBusinessType(SALES_TASK_BUSINESS_TYPE);
            message.setBusinessId(task.getId());
        } else if ("SENT".equals(message.getStatus()) && Objects.equals(message.getSendAt(), task.getReminderAt())) {
            return;
        }
        message.setTargetUserId(task.getOwnerId());
        message.setTitle(buildReminderTitle(task));
        message.setContent(buildReminderContent(task));
        message.setLevel(SalesTaskPriority.HIGH == task.getPriority() ? "IMPORTANT" : "INFO");
        message.setSendAt(task.getReminderAt());
        message.setStatus(MESSAGE_STATUS_PENDING);
        message.setLockedAt(null);
        message.setLockedBy(null);
        message.setSentAt(null);
        message.setRetryCount(Integer.valueOf(0));
        message.setNextRetryAt(null);
        message.setErrorMessage(null);
        message.setUpdatedAt(now);
        if (creating) {
            localMessageMapper.insert(message);
        } else {
            localMessageMapper.updateById(message);
        }
    }

    private boolean shouldCreateReminderMessage(SalesTaskEntity task) {
        if (task.isDeleted() || task.getReminderAt() == null || task.getOwnerId() == null) {
            return false;
        }
        return SalesTaskStatus.PENDING == task.getStatus()
                || SalesTaskStatus.IN_PROGRESS == task.getStatus()
                || SalesTaskStatus.OVERDUE == task.getStatus();
    }

    private void cancelReminderMessage(LocalMessageEntity message) {
        if (message == null) {
            return;
        }
        if ("SENT".equals(message.getStatus()) || MESSAGE_STATUS_CANCELLED.equals(message.getStatus())) {
            return;
        }
        message.setStatus(MESSAGE_STATUS_CANCELLED);
        message.setUpdatedAt(DateTimes.now());
        localMessageMapper.updateById(message);
    }

    private LocalMessageEntity findTaskReminderMessage(Long tenantId, Long taskId) {
        QueryWrapper<LocalMessageEntity> wrapper = new QueryWrapper<LocalMessageEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.eq("message_type", TASK_REMINDER_MESSAGE_TYPE);
        wrapper.eq("business_type", SALES_TASK_BUSINESS_TYPE);
        wrapper.eq("business_id", taskId);
        wrapper.last("limit 1");
        return localMessageMapper.selectOne(wrapper);
    }

    private String buildReminderTitle(SalesTaskEntity task) {
        String title = trimToNull(task.getTitle());
        if (title == null) {
            title = "销售任务";
        }
        return shrink("销售任务提醒：" + title, 128);
    }

    private String buildReminderContent(SalesTaskEntity task) {
        StringBuilder builder = new StringBuilder();
        builder.append("任务「").append(trimToNull(task.getTitle()) == null ? "未命名任务" : task.getTitle().trim())
                .append("」已到提醒时间，请及时处理。");
        builder.append("\n到期时间：").append(formatLocalDateTime(task.getDueAt()));
        if (trimToNull(task.getTargetName()) != null) {
            builder.append("\n关联对象：").append(task.getTargetName().trim());
        }
        if (trimToNull(task.getContent()) != null) {
            builder.append("\n任务内容：").append(task.getContent().trim());
        }
        return builder.toString();
    }

    private String formatLocalDateTime(LocalDateTime value) {
        if (value == null) {
            return "-";
        }
        return DATE_TIME_FORMATTER.format(value);
    }

    private String shrink(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private SalesTaskEntity findFollowupTask(Long tenantId, Long followupId) {
        QueryWrapper<SalesTaskEntity> wrapper = new QueryWrapper<SalesTaskEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        wrapper.eq("source", SalesTaskSource.FOLLOWUP.name());
        wrapper.eq("source_id", followupId);
        wrapper.last("limit 1");
        return salesTaskMapper.selectOne(wrapper);
    }

    private QueryWrapper<SalesTaskEntity> buildPageWrapper(
            Long tenantId, Long userId, String dataScope, SalesTaskQuery query) {
        QueryWrapper<SalesTaskEntity> wrapper = new QueryWrapper<SalesTaskEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        appendOwnerScope(wrapper, tenantId, userId, dataScope, query.getOwnerId());
        appendStatusFilter(wrapper, query.getStatus());
        if (query.getPriority() != null) {
            wrapper.eq("priority", query.getPriority().name());
        }
        if (query.getSource() != null) {
            wrapper.eq("source", query.getSource().name());
        }
        if (query.getTargetType() != null) {
            wrapper.eq("target_type", query.getTargetType().name());
        }
        if (query.getTargetId() != null) {
            wrapper.eq("target_id", query.getTargetId());
        }
        if (query.getDueFrom() != null) {
            wrapper.ge("due_at", query.getDueFrom());
        }
        if (query.getDueTo() != null) {
            wrapper.lt("due_at", query.getDueTo());
        }
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .like("title", keyword)
                    .or()
                    .like("content", keyword)
                    .or()
                    .like("target_name", keyword));
        }
        return wrapper;
    }

    private List<SalesTaskTargetOptionResponse> queryLeadTargetOptions(
            Long tenantId, Long userId, String dataScope, SalesTaskTargetOptionQuery query) {
        QueryWrapper<LeadEntity> wrapper = new QueryWrapper<LeadEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        appendOwnerScopeToGenericWrapper(wrapper, tenantId, userId, dataScope);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .like("name", keyword)
                    .or()
                    .like("company_name", keyword)
                    .or()
                    .like("phone", keyword));
        }
        wrapper.orderByDesc("created_at").last("limit " + safeOptionLimit(query.getLimit()));
        List<LeadEntity> entities = leadMapper.selectList(wrapper);
        List<SalesTaskTargetOptionResponse> options = new ArrayList<SalesTaskTargetOptionResponse>();
        for (LeadEntity entity : entities) {
            SalesTaskTargetOptionResponse option = new SalesTaskTargetOptionResponse();
            option.setId(entity.getId());
            option.setTargetType(SalesTaskTargetType.LEAD);
            option.setName(resolveText(entity.getCompanyName(), entity.getName()));
            option.setDescription(resolveDescription("线索", entity.getName(), entity.getPhone()));
            option.setOwnerId(entity.getOwnerId());
            options.add(option);
        }
        fillTargetOwnerNames(tenantId, options);
        return options;
    }

    private List<SalesTaskTargetOptionResponse> queryCustomerTargetOptions(
            Long tenantId, Long userId, String dataScope, SalesTaskTargetOptionQuery query) {
        QueryWrapper<CustomerEntity> wrapper = new QueryWrapper<CustomerEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        appendOwnerScopeToGenericWrapper(wrapper, tenantId, userId, dataScope);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .like("name", keyword)
                    .or()
                    .like("contact_name", keyword)
                    .or()
                    .like("contact_phone", keyword));
        }
        wrapper.orderByDesc("created_at").last("limit " + safeOptionLimit(query.getLimit()));
        List<CustomerEntity> entities = customerMapper.selectList(wrapper);
        List<SalesTaskTargetOptionResponse> options = new ArrayList<SalesTaskTargetOptionResponse>();
        for (CustomerEntity entity : entities) {
            SalesTaskTargetOptionResponse option = new SalesTaskTargetOptionResponse();
            option.setId(entity.getId());
            option.setTargetType(SalesTaskTargetType.CUSTOMER);
            option.setName(entity.getName());
            option.setDescription(resolveDescription("客户", entity.getContactName(), entity.getContactPhone()));
            option.setOwnerId(entity.getOwnerId());
            options.add(option);
        }
        fillTargetOwnerNames(tenantId, options);
        return options;
    }

    private List<SalesTaskTargetOptionResponse> queryOpportunityTargetOptions(
            Long tenantId, Long userId, String dataScope, SalesTaskTargetOptionQuery query) {
        QueryWrapper<OpportunityEntity> wrapper = new QueryWrapper<OpportunityEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        appendOwnerScopeToGenericWrapper(wrapper, tenantId, userId, dataScope);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .like("name", keyword)
                    .or()
                    .like("remark", keyword));
        }
        wrapper.orderByDesc("created_at").last("limit " + safeOptionLimit(query.getLimit()));
        List<OpportunityEntity> entities = opportunityMapper.selectList(wrapper);
        List<SalesTaskTargetOptionResponse> options = new ArrayList<SalesTaskTargetOptionResponse>();
        for (OpportunityEntity entity : entities) {
            SalesTaskTargetOptionResponse option = new SalesTaskTargetOptionResponse();
            option.setId(entity.getId());
            option.setTargetType(SalesTaskTargetType.OPPORTUNITY);
            option.setName(entity.getName());
            option.setDescription(resolveDescription("商机", entity.getStage() == null ? null : entity.getStage().name(), null));
            option.setOwnerId(entity.getOwnerId());
            options.add(option);
        }
        fillTargetOwnerNames(tenantId, options);
        return options;
    }

    private List<SalesTaskTargetOptionResponse> queryChannelTargetOptions(
            Long tenantId, Long userId, String dataScope, SalesTaskTargetOptionQuery query) {
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        appendOwnerScopeToGenericWrapper(wrapper, tenantId, userId, dataScope);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .like("title", keyword)
                    .or()
                    .like("company_name", keyword)
                    .or()
                    .like("contact_name", keyword)
                    .or()
                    .like("phone", keyword));
        }
        wrapper.orderByDesc("created_at").last("limit " + safeOptionLimit(query.getLimit()));
        List<ChannelRecordEntity> entities = channelRecordMapper.selectList(wrapper);
        List<SalesTaskTargetOptionResponse> options = new ArrayList<SalesTaskTargetOptionResponse>();
        for (ChannelRecordEntity entity : entities) {
            SalesTaskTargetOptionResponse option = new SalesTaskTargetOptionResponse();
            option.setId(entity.getId());
            option.setTargetType(SalesTaskTargetType.CHANNEL);
            option.setName(resolveText(entity.getCompanyName(), entity.getTitle()));
            option.setDescription(resolveDescription("渠道", entity.getContactName(), entity.getPhone()));
            option.setOwnerId(entity.getOwnerId());
            options.add(option);
        }
        fillTargetOwnerNames(tenantId, options);
        return options;
    }

    private <T> void appendOwnerScopeToGenericWrapper(
            QueryWrapper<T> wrapper, Long tenantId, Long userId, String dataScope) {
        if (userDataScopeValidator == null) {
            if ("SELF".equals(dataScope)) {
                wrapper.eq("owner_id", userId);
            }
            return;
        }
        if ("ALL".equals(dataScope)) {
            return;
        }
        List<Long> userIds = userDataScopeValidator.listAccessibleUserIds(tenantId, userId, dataScope);
        if (userIds == null || userIds.isEmpty()) {
            wrapper.eq("owner_id", -1L);
            return;
        }
        wrapper.in("owner_id", userIds);
    }

    private int safeOptionLimit(Integer limit) {
        if (limit == null || limit.intValue() < 1) {
            return 20;
        }
        return Math.min(limit.intValue(), 50);
    }

    private void appendOwnerScope(
            QueryWrapper<SalesTaskEntity> wrapper,
            Long tenantId,
            Long userId,
            String dataScope,
            Long ownerId) {
        if (ownerId != null) {
            checkOwnerAccess(tenantId, userId, dataScope, ownerId);
            wrapper.eq("owner_id", ownerId);
            return;
        }
        if (userDataScopeValidator == null) {
            if ("SELF".equals(dataScope)) {
                wrapper.eq("owner_id", userId);
            }
            return;
        }
        if ("ALL".equals(dataScope)) {
            return;
        }
        List<Long> userIds = userDataScopeValidator.listAccessibleUserIds(tenantId, userId, dataScope);
        if (userIds == null || userIds.isEmpty()) {
            wrapper.eq("owner_id", -1L);
            return;
        }
        wrapper.in("owner_id", userIds);
    }

    private void appendStatusFilter(QueryWrapper<SalesTaskEntity> wrapper, SalesTaskStatus status) {
        if (status == null) {
            return;
        }
        if (SalesTaskStatus.OVERDUE == status) {
            wrapper.in("status", SalesTaskStatus.PENDING.name(), SalesTaskStatus.IN_PROGRESS.name(), SalesTaskStatus.OVERDUE.name());
            wrapper.lt("due_at", DateTimes.now());
            return;
        }
        wrapper.eq("status", status.name());
        if (SalesTaskStatus.PENDING == status || SalesTaskStatus.IN_PROGRESS == status) {
            wrapper.ge("due_at", DateTimes.now());
        }
    }

    private SalesTaskEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("TASK_001", "任务编号不能为空");
        }
        SalesTaskEntity entity = salesTaskMapper.selectOne(Wrappers.<SalesTaskEntity>lambdaQuery()
                .eq(SalesTaskEntity::getId, id)
                .eq(SalesTaskEntity::getTenantId, tenantId)
                .eq(SalesTaskEntity::isDeleted, false));
        if (entity == null) {
            throw new BusinessException("TASK_002", "任务不存在");
        }
        return entity;
    }

    private void checkOwnerAccess(Long tenantId, Long operatorId, String dataScope, Long ownerId) {
        if (userDataScopeValidator != null) {
            userDataScopeValidator.checkOwnerAccess(tenantId, operatorId, dataScope, ownerId);
            return;
        }
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(operatorId))) {
            throw new BusinessException("DATA_001", "无权访问该数据");
        }
    }

    private void validateSaveRequest(SalesTaskSaveRequest request) {
        if (request == null) {
            throw new BusinessException("TASK_003", "任务信息不能为空");
        }
        if (trimToNull(request.getTitle()) == null) {
            throw new BusinessException("TASK_004", "任务标题不能为空");
        }
        if (request.getDueAt() == null) {
            throw new BusinessException("TASK_005", "到期时间不能为空");
        }
    }

    private SalesTaskTargetType convertTargetType(FollowupTargetType targetType) {
        if (FollowupTargetType.LEAD == targetType) {
            return SalesTaskTargetType.LEAD;
        }
        if (FollowupTargetType.CUSTOMER == targetType) {
            return SalesTaskTargetType.CUSTOMER;
        }
        if (FollowupTargetType.OPPORTUNITY == targetType) {
            return SalesTaskTargetType.OPPORTUNITY;
        }
        return null;
    }

    private LocalDateTime resolveReminderTime(LocalDateTime dueAt) {
        if (dueAt == null) {
            return null;
        }
        return dueAt.minusMinutes(30);
    }

    private String buildFollowupTaskTitle(FollowupRecordEntity followup) {
        String targetName = trimToNull(followup.getTargetName());
        if (targetName == null) {
            targetName = String.valueOf(followup.getTargetId());
        }
        return "跟进：" + targetName;
    }

    private SalesTaskResponse toResponse(SalesTaskEntity entity) {
        SalesTaskResponse response = new SalesTaskResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setTitle(entity.getTitle());
        response.setContent(entity.getContent());
        response.setTargetType(entity.getTargetType());
        response.setTargetId(entity.getTargetId());
        response.setTargetName(entity.getTargetName());
        response.setOwnerId(entity.getOwnerId());
        response.setCreatorId(entity.getCreatorId());
        response.setDueAt(entity.getDueAt());
        response.setReminderAt(entity.getReminderAt());
        response.setPriority(entity.getPriority());
        response.setStatus(resolveStatus(entity));
        response.setSource(entity.getSource());
        response.setSourceId(entity.getSourceId());
        response.setCompletedAt(entity.getCompletedAt());
        response.setCompletedBy(entity.getCompletedBy());
        response.setCancelReason(entity.getCancelReason());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private SalesTaskStatus resolveStatus(SalesTaskEntity entity) {
        if (entity.getStatus() == null) {
            return SalesTaskStatus.PENDING;
        }
        if ((SalesTaskStatus.PENDING == entity.getStatus() || SalesTaskStatus.IN_PROGRESS == entity.getStatus())
                && entity.getDueAt() != null
                && entity.getDueAt().isBefore(DateTimes.now())) {
            return SalesTaskStatus.OVERDUE;
        }
        return entity.getStatus();
    }

    private void fillUserName(Long tenantId, SalesTaskResponse response) {
        List<SalesTaskResponse> records = new ArrayList<SalesTaskResponse>();
        records.add(response);
        fillUserNames(tenantId, records);
    }

    private void fillUserNames(Long tenantId, List<SalesTaskResponse> records) {
        if (userNameResolver == null || records == null || records.isEmpty()) {
            return;
        }
        Set<Long> userIds = new HashSet<Long>();
        for (SalesTaskResponse response : records) {
            if (response.getOwnerId() != null) {
                userIds.add(response.getOwnerId());
            }
            if (response.getCreatorId() != null) {
                userIds.add(response.getCreatorId());
            }
            if (response.getCompletedBy() != null) {
                userIds.add(response.getCompletedBy());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userNameResolver.resolve(tenantId, userIds);
        for (SalesTaskResponse response : records) {
            response.setOwnerName(names.get(response.getOwnerId()));
            response.setCreatorName(names.get(response.getCreatorId()));
            response.setCompletedByName(names.get(response.getCompletedBy()));
        }
    }

    private void fillTargetOwnerNames(Long tenantId, List<SalesTaskTargetOptionResponse> options) {
        if (userNameResolver == null || options == null || options.isEmpty()) {
            return;
        }
        Set<Long> ownerIds = new HashSet<Long>();
        for (SalesTaskTargetOptionResponse option : options) {
            if (option.getOwnerId() != null) {
                ownerIds.add(option.getOwnerId());
            }
        }
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userNameResolver.resolve(tenantId, ownerIds);
        for (SalesTaskTargetOptionResponse option : options) {
            option.setOwnerName(names.get(option.getOwnerId()));
        }
    }

    private String resolveText(String first, String second) {
        if (trimToNull(first) != null) {
            return first.trim();
        }
        if (trimToNull(second) != null) {
            return second.trim();
        }
        return "-";
    }

    private String resolveDescription(String prefix, String first, String second) {
        StringBuilder builder = new StringBuilder(prefix == null ? "" : prefix);
        String text = trimToNull(first);
        if (text != null) {
            builder.append(" · ").append(text);
        }
        text = trimToNull(second);
        if (text != null) {
            builder.append(" · ").append(text);
        }
        return builder.toString();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
