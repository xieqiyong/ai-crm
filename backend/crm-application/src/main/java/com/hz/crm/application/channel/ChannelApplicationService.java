package com.hz.crm.application.channel;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.application.channel.dto.ChannelDocumentImportRequest;
import com.hz.crm.application.channel.dto.ChannelMediaImportRequest;
import com.hz.crm.application.channel.dto.ChannelPromoteRequest;
import com.hz.crm.application.channel.dto.ChannelQuery;
import com.hz.crm.application.channel.dto.ChannelResponse;
import com.hz.crm.application.channel.dto.ChannelSaveRequest;
import com.hz.crm.application.lead.LeadApplicationService;
import com.hz.crm.application.lead.dto.LeadResponse;
import com.hz.crm.application.lead.dto.LeadSaveRequest;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.UserNameResolver;
import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.ChannelSource;
import com.hz.crm.domain.channel.ChannelStatus;
import com.hz.crm.domain.channel.ChannelType;
import com.hz.crm.domain.channel.mapper.ChannelRecordMapper;
import com.hz.crm.domain.lead.LeadStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChannelApplicationService {

    @Autowired
    private ChannelRecordMapper channelRecordMapper;

    @Autowired
    private LeadApplicationService leadApplicationService;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired(required = false)
    private UserNameResolver userNameResolver;

    @Transactional(readOnly = true)
    public PageData<ChannelResponse> page(Long tenantId, Long userId, String dataScope, ChannelQuery query) {
        ChannelQuery safeQuery = query == null ? new ChannelQuery() : query;
        long total = channelRecordMapper.selectCount(buildQueryWrapper(tenantId, userId, dataScope, safeQuery));
        QueryWrapper<ChannelRecordEntity> wrapper = buildQueryWrapper(tenantId, userId, dataScope, safeQuery);
        int pageNo = safeQuery.safePageNo();
        int pageSize = safeQuery.safePageSize();
        int offset = (pageNo - 1) * pageSize;
        wrapper.orderByDesc("created_at").last("limit " + pageSize + " offset " + offset);
        List<ChannelRecordEntity> entities = channelRecordMapper.selectList(wrapper);
        List<ChannelResponse> records = new ArrayList<ChannelResponse>();
        for (ChannelRecordEntity entity : entities) {
            records.add(toResponse(entity));
        }
        fillOwnerNames(tenantId, records);
        return PageData.of(total, pageNo, pageSize, records);
    }

    @Transactional(readOnly = true)
    public ChannelResponse detail(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse save(Long tenantId, Long operatorId, String dataScope, ChannelSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BusinessException("CHANNEL_001", "渠道标题不能为空");
        }
        ChannelRecordEntity entity;
        LocalDateTime now = DateTimes.now();
        if (request.getId() == null) {
            entity = new ChannelRecordEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setOwnerId(operatorId);
            entity.setStatus(ChannelStatus.NEW);
            entity.setCreatedAt(now);
        } else {
            entity = findOne(tenantId, request.getId());
            checkDataScope(operatorId, dataScope, entity.getOwnerId());
        }
        entity.setUpdatedAt(now);
        entity.setTitle(trimToNull(request.getTitle()));
        entity.setChannelType(request.getChannelType() == null ? ChannelType.MANUAL : request.getChannelType());
        entity.setSource(normalizeSource(request.getSource()));
        entity.setContactName(trimToNull(request.getContactName()));
        entity.setCompanyName(trimToNull(request.getCompanyName()));
        entity.setPhone(trimToNull(request.getPhone()));
        entity.setEmail(trimToNull(request.getEmail()));
        entity.setRemark(trimToNull(request.getRemark()));
        if (request.getId() == null) {
            channelRecordMapper.insert(entity);
        } else {
            channelRecordMapper.updateById(entity);
        }
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public void delete(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        entity.setDeleted(true);
        entity.setUpdatedAt(DateTimes.now());
        channelRecordMapper.updateById(entity);
    }

    @Transactional
    public ChannelResponse importMedia(Long tenantId, Long operatorId, ChannelMediaImportRequest request) {
        if (request == null || !StringUtils.hasText(request.getMediaFileName())) {
            throw new BusinessException("CHANNEL_002", "请上传录音或视频文件");
        }
        ChannelRecordEntity entity = new ChannelRecordEntity();
        LocalDateTime now = DateTimes.now();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(tenantId);
        entity.setOwnerId(operatorId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setTitle(resolveMediaTitle(request));
        entity.setChannelType(resolveMediaType(request));
        entity.setStatus(ChannelStatus.WAITING_TRANSCRIPTION);
        entity.setSource(normalizeSource(request.getSource()));
        entity.setContactName(trimToNull(request.getContactName()));
        entity.setCompanyName(trimToNull(request.getCompanyName()));
        entity.setPhone(trimToNull(request.getPhone()));
        entity.setEmail(trimToNull(request.getEmail()));
        entity.setMediaFileName(trimToNull(request.getMediaFileName()));
        entity.setMediaContentType(trimToNull(request.getMediaContentType()));
        entity.setMediaSize(request.getMediaSize());
        entity.setMediaStorageKey(trimToNull(request.getMediaStorageKey()));
        entity.setRemark(trimToNull(request.getRemark()));
        channelRecordMapper.insert(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse importDocument(Long tenantId, Long operatorId, ChannelDocumentImportRequest request) {
        if (request == null || !StringUtils.hasText(request.getMediaFileName())) {
            throw new BusinessException("CHANNEL_010", "请上传文档或HTML页面");
        }
        if (!StringUtils.hasText(request.getDocumentText())) {
            throw new BusinessException("CHANNEL_011", "文档内容为空，无法提取关键信息");
        }
        ChannelDocumentProfile profile = extractDocumentProfile(request.getDocumentText());
        ChannelRecordEntity entity = new ChannelRecordEntity();
        LocalDateTime now = DateTimes.now();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTenantId(tenantId);
        entity.setOwnerId(operatorId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setTitle(firstText(request.getTitle(), profile.getTitle(), request.getMediaFileName()));
        entity.setChannelType(ChannelType.DOCUMENT);
        entity.setStatus(ChannelStatus.ANALYZED);
        entity.setSource(normalizeSource(request.getSource()));
        entity.setContactName(firstText(request.getContactName(), profile.getContactName()));
        entity.setCompanyName(firstText(request.getCompanyName(), profile.getCompanyName()));
        entity.setPhone(firstText(request.getPhone(), profile.getPhone()));
        entity.setEmail(firstText(request.getEmail(), profile.getEmail()));
        entity.setMediaFileName(trimToNull(request.getMediaFileName()));
        entity.setMediaContentType(trimToNull(request.getMediaContentType()));
        entity.setMediaSize(request.getMediaSize());
        entity.setMediaStorageKey(trimToNull(request.getMediaStorageKey()));
        entity.setTranscriptText(limitText(request.getDocumentText(), 12000));
        entity.setAiSummary(profile.getSummary());
        entity.setUsefulInfo(profile.getUsefulInfo());
        entity.setRemark(limitText(firstText(request.getRemark(), profile.getRemark()), 512));
        channelRecordMapper.insert(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse prepareTranscription(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        checkNotPromoted(entity);
        entity.setStatus(ChannelStatus.WAITING_TRANSCRIPTION);
        entity.setUpdatedAt(DateTimes.now());
        channelRecordMapper.updateById(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse prepareAiAnalysis(Long tenantId, Long userId, String dataScope, Long id) {
        ChannelRecordEntity entity = findOne(tenantId, id);
        checkDataScope(userId, dataScope, entity.getOwnerId());
        checkNotPromoted(entity);
        entity.setStatus(ChannelStatus.WAITING_AI_ANALYSIS);
        entity.setUpdatedAt(DateTimes.now());
        channelRecordMapper.updateById(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    @Transactional
    public ChannelResponse promoteToLead(
            Long tenantId, Long operatorId, String dataScope, ChannelPromoteRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException("CHANNEL_003", "渠道编号不能为空");
        }
        ChannelRecordEntity entity = findOne(tenantId, request.getId());
        checkDataScope(operatorId, dataScope, entity.getOwnerId());
        if (entity.getLeadId() != null) {
            throw new BusinessException("CHANNEL_004", "渠道已晋升为线索");
        }
        LeadSaveRequest leadRequest = new LeadSaveRequest();
        leadRequest.setName(resolveLeadName(entity));
        leadRequest.setCompanyName(entity.getCompanyName());
        leadRequest.setPhone(entity.getPhone());
        leadRequest.setEmail(entity.getEmail());
        leadRequest.setSource(resolveLeadSource(entity));
        leadRequest.setStatus(LeadStatus.recommended());
        leadRequest.setOwnerId(request.getOwnerId() == null ? entity.getOwnerId() : request.getOwnerId());
        leadRequest.setRemark(resolveLeadRemark(entity));
        LeadResponse lead = leadApplicationService.save(tenantId, operatorId, dataScope, leadRequest);
        entity.setLeadId(lead.getId());
        entity.setStatus(ChannelStatus.PROMOTED);
        entity.setUpdatedAt(DateTimes.now());
        channelRecordMapper.updateById(entity);
        ChannelResponse response = toResponse(entity);
        fillOwnerName(tenantId, response);
        return response;
    }

    private QueryWrapper<ChannelRecordEntity> buildQueryWrapper(
            Long tenantId, Long userId, String dataScope, ChannelQuery query) {
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        if ("SELF".equals(dataScope)) {
            wrapper.eq("owner_id", userId);
        }
        if (query.getStatus() != null) {
            wrapper.eq("status", query.getStatus().name());
        }
        if (query.getChannelType() != null) {
            wrapper.eq("channel_type", query.getChannelType().name());
        }
        String keyword = likeKeyword(query.getKeyword());
        if (keyword != null) {
            wrapper.and(value -> value
                    .apply("lower(coalesce(title, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(source, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(contact_name, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(company_name, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(phone, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(email, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(ai_summary, '')) like {0}", keyword)
                    .or()
                    .apply("lower(coalesce(useful_info, '')) like {0}", keyword));
        }
        return wrapper;
    }

    private ChannelRecordEntity findOne(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("CHANNEL_003", "渠道编号不能为空");
        }
        QueryWrapper<ChannelRecordEntity> wrapper = new QueryWrapper<ChannelRecordEntity>();
        wrapper.eq("id", id);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("deleted", false);
        ChannelRecordEntity entity = channelRecordMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException("CHANNEL_005", "渠道记录不存在");
        }
        return entity;
    }

    private void checkDataScope(Long userId, String dataScope, Long ownerId) {
        if ("SELF".equals(dataScope) && (ownerId == null || !ownerId.equals(userId))) {
            throw new BusinessException("DATA_001", "无权访问该渠道记录");
        }
    }

    private void checkNotPromoted(ChannelRecordEntity entity) {
        if (entity.getLeadId() != null) {
            throw new BusinessException("CHANNEL_007", "已晋升线索的渠道不能回退处理状态");
        }
    }

    private ChannelDocumentProfile extractDocumentProfile(String documentText) {
        String text = normalizeText(documentText);
        ChannelDocumentProfile profile = new ChannelDocumentProfile();
        profile.setTitle(extractTitle(text));
        profile.setContactName(extractContactName(text));
        profile.setCompanyName(extractCompanyName(text));
        profile.setPhone(extractRegexGroup(text, "(?<!\\d)1[3-9]\\d{9}(?!\\d)", 0));
        profile.setEmail(extractRegexGroup(text, "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", 0));
        profile.setSummary(buildDocumentSummary(text, profile));
        profile.setUsefulInfo(buildDocumentUsefulInfo(text));
        profile.setRemark(buildDocumentRemark(text, profile));
        return profile;
    }

    private String extractTitle(String text) {
        String title = extractBetween(text, "用户电话沟通分析", "用户画像");
        if (StringUtils.hasText(title)) {
            return "用户电话沟通分析" + title;
        }
        title = extractRegexGroup(text, "([^\\s]{2,80}(沟通|纪要|分析|记录)[^\\s]{0,40})", 1);
        return trimToNull(title);
    }

    private String extractContactName(String text) {
        String name = extractRegexGroup(text, "用户电话沟通分析\\s*·\\s*([^\\s·，。]+)", 1);
        if (StringUtils.hasText(name)) {
            return cleanPersonName(name);
        }
        name = extractRegexGroup(text, "(联系人|客户|用户|姓名)[:：\\s]+([^\\s，。；;]+)", 2);
        if (StringUtils.hasText(name)) {
            return cleanPersonName(name);
        }
        return null;
    }

    private String extractCompanyName(String text) {
        String companyName = extractRegexGroup(text, "(公司名称|公司|企业名称|企业)[:：\\s]+([^，。；;\\s]{2,80})", 2);
        if (!StringUtils.hasText(companyName)) {
            return null;
        }
        if (companyName.contains("下级") || companyName.contains("集团型")) {
            return null;
        }
        return companyName.trim();
    }

    private String buildDocumentSummary(String text, ChannelDocumentProfile profile) {
        StringBuilder builder = new StringBuilder();
        builder.append("### 渠道材料提取摘要\n\n");
        appendLine(builder, "材料标题", profile.getTitle());
        appendLine(builder, "联系人", profile.getContactName());
        appendLine(builder, "公司名称", profile.getCompanyName());
        appendLine(builder, "联系电话", profile.getPhone());
        appendLine(builder, "联系邮箱", profile.getEmail());
        appendLine(builder, "客户分级", extractLevel(text));
        appendLine(builder, "客户画像", extractAfter(text, "用户画像速览", "二、现有技术栈", 180));
        appendLine(builder, "核心诉求", extractDemands(text));
        appendLine(builder, "匹配判断", extractAfter(text, "总体判断：", "五、用户顾虑", 260));
        appendLine(builder, "顾虑风险", extractConcerns(text));
        appendLine(builder, "下一步动作", extractNextActions(text));
        return builder.toString();
    }

    private String buildDocumentUsefulInfo(String text) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "角色", extractAfter(text, "角色", "从业经验", 80));
        appendLine(builder, "从业经验", extractAfter(text, "从业经验", "态度", 80));
        appendLine(builder, "组织形态", extractAfter(text, "组织形态", "团队定位", 100));
        appendLine(builder, "技术现状", extractTechStack(text));
        appendLine(builder, "明确需求", extractDemands(text));
        appendLine(builder, "隐含需求", extractAfter(text, "3.2 隐含需求", "四、与", 260));
        appendLine(builder, "内部协同", extractAfter(text, "需内部协同", "九、表单跟进记录", 260));
        return builder.toString();
    }

    private String buildDocumentRemark(String text, ChannelDocumentProfile profile) {
        String copyRecord = extractAfter(text, "复制以下内容至", "用户电话沟通分析", 420);
        if (StringUtils.hasText(copyRecord)) {
            return copyRecord;
        }
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "联系人", profile.getContactName());
        appendLine(builder, "核心诉求", extractDemands(text));
        appendLine(builder, "下一步", extractNextActions(text));
        return builder.toString();
    }

    private String extractLevel(String text) {
        if (text.contains("A 类线索")) {
            return "A 类线索";
        }
        if (text.contains("B 类线索")) {
            return "B 类线索";
        }
        if (text.contains("C 类线索")) {
            return "C 类线索";
        }
        String stars = extractRegexGroup(text, "★{1,5}", 0);
        return trimToNull(stars);
    }

    private String extractTechStack(String text) {
        List<String> values = new ArrayList<String>();
        addIfContains(values, text, "Zabbix", "Zabbix 监控");
        addIfContains(values, text, "华为 APM", "华为 APM");
        addIfContains(values, text, "ELK", "ELK 生产日志");
        addIfContains(values, text, "私有云", "私有云核心业务");
        addIfContains(values, text, "华为公有云", "华为公有云");
        addIfContains(values, text, "算力卡", "算力卡 + 小模型");
        return joinValues(values);
    }

    private String extractDemands(String text) {
        List<String> values = new ArrayList<String>();
        addIfContains(values, text, "商业 APM", "私有云核心业务采购商业 APM");
        addIfContains(values, text, "统一智能管控", "统一智能管控平台");
        addIfContains(values, text, "ELK 日志", "ELK 日志 + 大模型智能分析");
        addIfContains(values, text, "下级公司赋能", "集团对下级公司赋能");
        addIfContains(values, text, "OEM", "OEM / 合作模式");
        addIfContains(values, text, "Mythos", "安全 AI / Mythos");
        return joinValues(values);
    }

    private String extractConcerns(String text) {
        List<String> values = new ArrayList<String>();
        addIfContains(values, text, "预算不确定", "预算不确定");
        addIfContains(values, text, "采购流程长", "采购流程长，需要 ROI 论证");
        addIfContains(values, text, "团队已饱和", "团队已饱和，难承担新平台建设");
        addIfContains(values, text, "下级赋能 ROI", "下级赋能 ROI 需要证明");
        return joinValues(values);
    }

    private String extractNextActions(String text) {
        List<String> values = new ArrayList<String>();
        addIfContains(values, text, "确认ELK/Zabbix规模", "确认 ELK / Zabbix 规模");
        addIfContains(values, text, "ELK问数", "安排 ELK 问数 + 巡检 Demo");
        addIfContains(values, text, "商务联合回访", "内部确认 OEM 政策后商务联合回访");
        addIfContains(values, text, "3–5 个工作日", "建议 3–5 个工作日内二次沟通");
        return joinValues(values);
    }

    private void addIfContains(List<String> values, String text, String keyword, String value) {
        if (text.contains(keyword) && !values.contains(value)) {
            values.add(value);
        }
    }

    private String joinValues(List<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        return String.join("；", values);
    }

    private String extractAfter(String text, String start, String end, int limit) {
        String value = extractBetween(text, start, end);
        return limitText(value, limit);
    }

    private String extractBetween(String text, String start, String end) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(start)) {
            return null;
        }
        int startIndex = text.indexOf(start);
        if (startIndex < 0) {
            return null;
        }
        int valueStart = startIndex + start.length();
        int endIndex = StringUtils.hasText(end) ? text.indexOf(end, valueStart) : -1;
        String value = endIndex < 0 ? text.substring(valueStart) : text.substring(valueStart, endIndex);
        return trimToNull(value);
    }

    private String extractRegexGroup(String text, String regex, int groupIndex) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        if (groupIndex > matcher.groupCount()) {
            return null;
        }
        return trimToNull(matcher.group(groupIndex));
    }

    private String cleanPersonName(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.replace("先生", "").replace("女士", "").replace("总", "").trim();
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
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

    private String resolveMediaTitle(ChannelMediaImportRequest request) {
        if (StringUtils.hasText(request.getTitle())) {
            return request.getTitle();
        }
        return request.getMediaFileName();
    }

    private ChannelType resolveMediaType(ChannelMediaImportRequest request) {
        if (request.getChannelType() != null && ChannelType.MANUAL != request.getChannelType()) {
            return request.getChannelType();
        }
        if (StringUtils.hasText(request.getMediaContentType())
                && request.getMediaContentType().toLowerCase().startsWith("video/")) {
            return ChannelType.VIDEO;
        }
        return ChannelType.AUDIO;
    }

    private String resolveLeadName(ChannelRecordEntity entity) {
        if (StringUtils.hasText(entity.getContactName())) {
            return entity.getContactName();
        }
        return entity.getTitle();
    }

    private String resolveLeadSource(ChannelRecordEntity entity) {
        String source = entity.getSource();
        if (!StringUtils.hasText(source)) {
            source = entity.getChannelType().name();
        }
        return "渠道管理-" + source;
    }

    private String resolveLeadRemark(ChannelRecordEntity entity) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "渠道标题", entity.getTitle());
        appendLine(builder, "渠道备注", entity.getRemark());
        appendLine(builder, "渠道材料", entity.getMediaFileName());
        appendLine(builder, "转译文本", entity.getTranscriptText());
        appendLine(builder, "AI总结", entity.getAiSummary());
        appendLine(builder, "有用信息", entity.getUsefulInfo());
        String remark = builder.toString();
        if (remark.length() > 512) {
            return remark.substring(0, 512);
        }
        return remark;
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(label).append("：").append(value);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeSource(String value) {
        return ChannelSource.from(value).name();
    }

    private String likeKeyword(String value) {
        String keyword = trimToNull(value);
        return keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }

    private ChannelResponse toResponse(ChannelRecordEntity entity) {
        ChannelResponse response = new ChannelResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setTitle(entity.getTitle());
        response.setChannelType(entity.getChannelType());
        response.setStatus(entity.getStatus());
        response.setSource(entity.getSource());
        response.setContactName(entity.getContactName());
        response.setCompanyName(entity.getCompanyName());
        response.setPhone(entity.getPhone());
        response.setEmail(entity.getEmail());
        response.setMediaFileName(entity.getMediaFileName());
        response.setMediaContentType(entity.getMediaContentType());
        response.setMediaSize(entity.getMediaSize());
        response.setMediaStorageKey(entity.getMediaStorageKey());
        response.setTranscriptText(entity.getTranscriptText());
        response.setAiSummary(entity.getAiSummary());
        response.setUsefulInfo(entity.getUsefulInfo());
        response.setLeadId(entity.getLeadId());
        response.setOwnerId(entity.getOwnerId());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private void fillOwnerName(Long tenantId, ChannelResponse response) {
        List<ChannelResponse> records = new ArrayList<ChannelResponse>();
        records.add(response);
        fillOwnerNames(tenantId, records);
    }

    private void fillOwnerNames(Long tenantId, List<ChannelResponse> records) {
        if (userNameResolver == null || records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ownerIds = new HashSet<Long>();
        for (ChannelResponse response : records) {
            if (response.getOwnerId() != null) {
                ownerIds.add(response.getOwnerId());
            }
        }
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userNameResolver.resolve(tenantId, ownerIds);
        for (ChannelResponse response : records) {
            if (response.getOwnerId() != null) {
                response.setOwnerName(names.get(response.getOwnerId()));
            }
        }
    }

    private static class ChannelDocumentProfile {

        private String title;

        private String contactName;

        private String companyName;

        private String phone;

        private String email;

        private String summary;

        private String usefulInfo;

        private String remark;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContactName() {
            return contactName;
        }

        public void setContactName(String contactName) {
            this.contactName = contactName;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getUsefulInfo() {
            return usefulInfo;
        }

        public void setUsefulInfo(String usefulInfo) {
            this.usefulInfo = usefulInfo;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
