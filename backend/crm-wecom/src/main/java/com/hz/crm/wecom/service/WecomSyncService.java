package com.hz.crm.wecom.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.application.channel.ChannelApplicationService;
import com.hz.crm.application.channel.dto.ExternalChannelSyncRequest;
import com.hz.crm.application.channel.dto.ExternalChannelSyncResult;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.wecom.WecomContactFollowEntity;
import com.hz.crm.domain.wecom.WecomCorpConfigEntity;
import com.hz.crm.domain.wecom.WecomExternalContactEntity;
import com.hz.crm.domain.wecom.WecomGroupChatEntity;
import com.hz.crm.domain.wecom.WecomGroupMemberEntity;
import com.hz.crm.domain.wecom.WecomSyncStatus;
import com.hz.crm.domain.wecom.WecomSyncTaskEntity;
import com.hz.crm.domain.wecom.mapper.WecomCorpConfigMapper;
import com.hz.crm.domain.wecom.mapper.WecomSyncTaskMapper;
import com.hz.crm.wecom.client.WecomApiClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WecomSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WecomSyncService.class);

    @Autowired
    private WecomSyncTaskMapper taskMapper;

    @Autowired
    private WecomCorpConfigMapper configMapper;

    @Autowired
    private WecomTokenService tokenService;

    @Autowired
    private WecomApiClient apiClient;

    @Autowired
    private WecomDataService dataService;

    @Autowired
    private ChannelApplicationService channelApplicationService;

    @Value("${crm.wecom.fetch-detail-enabled:true}")
    private boolean fetchDetailEnabled;

    @Value("${crm.wecom.fetch-tags-enabled:true}")
    private boolean fetchTagsEnabled;

    public void execute(Long taskId) {
        WecomSyncTaskEntity task = taskMapper.selectById(taskId);
        if (task == null || task.isDeleted()) {
            return;
        }
        WecomCorpConfigEntity config = configMapper.selectById(task.getConfigId());
        if (config == null || config.isDeleted()) {
            fail(task, null, "企业微信配置不存在");
            return;
        }
        RLock lock = tokenService.syncLock(task.getTenantId(), task.getConfigId());
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.MINUTES);
            if (!locked) {
                skip(task, config, "已有同步任务正在执行");
                return;
            }
            start(task, config);
            runSync(task, config);
            success(task, config);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail(task, config, "企业微信同步任务被中断");
        } catch (Exception ex) {
            LOGGER.error("企业微信同步失败，任务编号：{}", taskId, ex);
            fail(task, config, safeMessage(ex));
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void runSync(WecomSyncTaskEntity task, WecomCorpConfigEntity config) {
        LOGGER.info(
                "企业微信同步开始，任务编号：{}，租户编号：{}，配置编号：{}",
                task.getId(),
                task.getTenantId(),
                config.getId());
        String accessToken = tokenService.getToken(config);
        List<String> followUsers = apiClient.listFollowUsers(accessToken);
        LOGGER.info("企业微信客户联系员工读取完成，任务编号：{}，员工数量：{}", task.getId(), followUsers.size());
        dataService.ensureBindings(task.getTenantId(), config.getId(), followUsers);
        Map<String, JSONObject> contactDetails = syncContacts(task, config, accessToken, followUsers);
        Map<String, JSONObject> groupDetails = syncGroups(task, config, accessToken, followUsers);
        Map<String, Map<String, Object>> corpTags = syncCorpTags(accessToken);
        syncChannels(task, config, corpTags, contactDetails, groupDetails);
    }

    private Map<String, JSONObject> syncContacts(
            WecomSyncTaskEntity task,
            WecomCorpConfigEntity config,
            String accessToken,
            List<String> followUsers) {
        List<JSONObject> records = apiClient.listExternalContacts(accessToken, followUsers);
        task.setContactsFetched(records.size());
        Set<String> externalUserIds = new LinkedHashSet<String>();
        for (JSONObject record : records) {
            WecomUpsertResult<WecomExternalContactEntity> contactResult =
                    dataService.upsertContact(task.getTenantId(), config.getId(), record);
            WecomExternalContactEntity contact = contactResult.getData();
            if (contact == null) {
                continue;
            }
            externalUserIds.add(contact.getExternalUserId());
            if (contactResult.isCreated()) {
                task.setContactsCreated(task.getContactsCreated() + 1);
            } else {
                task.setContactsUpdated(task.getContactsUpdated() + 1);
            }
            JSONObject followInfo = record.getJSONObject("follow_info");
            dataService.upsertFollow(
                    task.getTenantId(),
                    config.getId(),
                    contact.getId(),
                    contact.getExternalUserId(),
                    followInfo,
                    config.getDefaultOwnerId());
        }
        task.setUpdatedAt(DateTimes.now());
        taskMapper.updateById(task);
        LOGGER.info(
                "企业微信外部联系人读取完成，任务编号：{}，客户数量：{}",
                task.getId(),
                task.getContactsFetched());
        Map<String, JSONObject> contactDetails = new LinkedHashMap<String, JSONObject>();
        syncContactDetails(task, config, accessToken, externalUserIds, contactDetails);
        return contactDetails;
    }

    private Map<String, JSONObject> syncGroups(
            WecomSyncTaskEntity task,
            WecomCorpConfigEntity config,
            String accessToken,
            List<String> followUsers) {
        List<String> chatIds = apiClient.listGroupChatIds(accessToken, followUsers);
        task.setGroupsFetched(chatIds.size());
        Map<String, JSONObject> groupDetails = new HashMap<String, JSONObject>();
        for (String chatId : chatIds) {
            JSONObject group = apiClient.getGroupChat(accessToken, chatId);
            groupDetails.put(chatId, group);
            WecomUpsertResult<WecomGroupChatEntity> groupResult =
                    dataService.upsertGroup(task.getTenantId(), config.getId(), group);
            WecomGroupChatEntity groupEntity = groupResult.getData();
            if (groupEntity == null) {
                continue;
            }
            JSONArray members = group.getJSONArray("member_list");
            if (members == null) {
                continue;
            }
            for (int index = 0; index < members.size(); index++) {
                JSONObject member = members.getJSONObject(index);
                if (member == null) {
                    continue;
                }
                task.setGroupMembersFetched(task.getGroupMembersFetched() + 1);
                dataService.upsertGroupMember(
                        task.getTenantId(), config.getId(), groupEntity.getId(), chatId, member);
                if (Integer.valueOf(2).equals(member.getInteger("type"))) {
                    JSONObject profile = new JSONObject();
                    profile.put("external_userid", member.getString("userid"));
                    profile.put("name", member.getString("name"));
                    profile.put("type", 2);
                    profile.put("unionid", member.getString("unionid"));
                    dataService.upsertContact(task.getTenantId(), config.getId(), profile);
                }
            }
        }
        task.setUpdatedAt(DateTimes.now());
        taskMapper.updateById(task);
        LOGGER.info(
                "企业微信客户群读取完成，任务编号：{}，客户群数量：{}，群成员数量：{}",
                task.getId(),
                task.getGroupsFetched(),
                task.getGroupMembersFetched());
        return groupDetails;
    }

    private void syncContactDetails(
            WecomSyncTaskEntity task,
            WecomCorpConfigEntity config,
            String accessToken,
            Set<String> externalUserIds,
            Map<String, JSONObject> contactDetails) {
        if (!fetchDetailEnabled || externalUserIds == null || externalUserIds.isEmpty()) {
            return;
        }
        int detailCount = 0;
        for (String externalUserId : externalUserIds) {
            try {
                JSONObject detail = apiClient.getExternalContact(accessToken, externalUserId);
                if (detail == null) {
                    continue;
                }
                contactDetails.put(externalUserId, detail);
                WecomUpsertResult<WecomExternalContactEntity> contactResult =
                        dataService.upsertContact(task.getTenantId(), config.getId(), detail);
                WecomExternalContactEntity contact = contactResult.getData();
                if (contact == null) {
                    continue;
                }
                JSONArray followUsers = detail.getJSONArray("follow_user");
                if (followUsers != null) {
                    for (int index = 0; index < followUsers.size(); index++) {
                        JSONObject followUser = followUsers.getJSONObject(index);
                        dataService.upsertFollow(
                                task.getTenantId(),
                                config.getId(),
                                contact.getId(),
                                contact.getExternalUserId(),
                                followUser,
                                config.getDefaultOwnerId());
                    }
                }
                detailCount++;
            } catch (RuntimeException ex) {
                LOGGER.warn(
                        "企业微信客户详情补充读取失败，任务编号：{}，外部联系人：{}，原因：{}",
                        task.getId(),
                        externalUserId,
                        ex.getMessage());
            }
        }
        LOGGER.info("企业微信客户详情补充完成，任务编号：{}，详情数量：{}", task.getId(), detailCount);
    }

    private Map<String, Map<String, Object>> syncCorpTags(String accessToken) {
        if (!fetchTagsEnabled) {
            return Collections.emptyMap();
        }
        try {
            return buildCorpTagMap(apiClient.listCorpTags(accessToken));
        } catch (RuntimeException ex) {
            LOGGER.warn("企业微信客户标签库读取失败，原因：{}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Map<String, Object>> buildCorpTagMap(JSONArray groups) {
        if (groups == null || groups.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> tags = new HashMap<String, Map<String, Object>>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            JSONObject group = groups.getJSONObject(groupIndex);
            if (group == null) {
                continue;
            }
            JSONArray tagArray = group.getJSONArray("tag");
            if (tagArray == null) {
                continue;
            }
            for (int tagIndex = 0; tagIndex < tagArray.size(); tagIndex++) {
                JSONObject tag = tagArray.getJSONObject(tagIndex);
                if (tag == null || !StringUtils.hasText(tag.getString("id"))) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("标签ID", tag.getString("id"));
                item.put("标签名称", tag.getString("name"));
                item.put("标签分组", group.getString("group_name"));
                item.put("标签排序", tag.getInteger("order"));
                item.put("标签已删除", tag.getBoolean("deleted"));
                tags.put(tag.getString("id"), item);
            }
        }
        return tags;
    }

    private void syncChannels(
            WecomSyncTaskEntity task,
            WecomCorpConfigEntity config,
            Map<String, Map<String, Object>> corpTags,
            Map<String, JSONObject> contactDetails,
            Map<String, JSONObject> groupDetails) {
        List<WecomExternalContactEntity> contacts =
                dataService.listContacts(task.getTenantId(), config.getId());
        task.setContactsFetched(contacts.size());
        for (WecomExternalContactEntity contact : contacts) {
            List<WecomContactFollowEntity> follows =
                    dataService.listFollows(task.getTenantId(), config.getId(), contact.getExternalUserId());
            List<WecomGroupMemberEntity> memberships =
                    dataService.listMemberships(task.getTenantId(), config.getId(), contact.getExternalUserId());
            String snapshot = buildSnapshot(
                    task.getTenantId(), config, contact, follows, memberships, corpTags, contactDetails, groupDetails);
            ExternalChannelSyncRequest request = new ExternalChannelSyncRequest();
            request.setExternalProvider("WECOM");
            request.setExternalKey(config.getCorpId() + ":" + contact.getExternalUserId());
            request.setExternalVersion(sha256(snapshot));
            request.setTitle("企业微信客户 · " + resolveContactName(contact));
            request.setSource(memberships.isEmpty() ? "WECHAT" : "WECHAT_GROUP");
            request.setContactName(contact.getName());
            request.setCompanyName(resolveCompanyName(contact, follows));
            request.setPhone(resolvePhone(follows, contact));
            request.setEmail(resolveEmail(contact));
            request.setOwnerId(resolveOwner(task.getTenantId(), config, follows, memberships));
            request.setSourceSnapshot(snapshot);
            ExternalChannelSyncResult result =
                    channelApplicationService.syncExternalChannel(task.getTenantId(), request);
            if (result.isCreated()) {
                task.setChannelsCreated(task.getChannelsCreated() + 1);
            } else if (result.isUpdated()) {
                task.setChannelsUpdated(task.getChannelsUpdated() + 1);
            } else {
                task.setDuplicatesSkipped(task.getDuplicatesSkipped() + 1);
            }
        }
        task.setUpdatedAt(DateTimes.now());
        taskMapper.updateById(task);
        LOGGER.info(
                "企业微信渠道同步完成，任务编号：{}，新增：{}，更新：{}，重复跳过：{}",
                task.getId(),
                task.getChannelsCreated(),
                task.getChannelsUpdated(),
                task.getDuplicatesSkipped());
    }

    private String buildSnapshot(
            Long tenantId,
            WecomCorpConfigEntity config,
            WecomExternalContactEntity contact,
            List<WecomContactFollowEntity> follows,
            List<WecomGroupMemberEntity> memberships,
            Map<String, Map<String, Object>> corpTags,
            Map<String, JSONObject> contactDetails,
            Map<String, JSONObject> groupDetails) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("数据来源", "企业微信客户联系主动同步");
        root.put("企业ID", config.getCorpId());
        root.put("同步摘要", buildSyncSummary(contact, follows, memberships));
        Map<String, Object> contactData = new LinkedHashMap<String, Object>();
        contactData.put("外部联系人ID", contact.getExternalUserId());
        contactData.put("姓名", contact.getName());
        contactData.put("客户类型", describeContactType(contact.getContactType()));
        contactData.put("客户类型编码", contact.getContactType());
        contactData.put("企业名称", resolveCompanyName(contact, follows));
        contactData.put("职位", contact.getPosition());
        contactData.put("性别", contact.getGender());
        contactData.put("联合ID", contact.getUnionId());
        contactData.put("头像", contact.getAvatar());
        root.put("客户基础信息", contactData);
        root.put("客户对外资料", parseExternalProfile(contact));

        List<Map<String, Object>> followData = new ArrayList<Map<String, Object>>();
        for (WecomContactFollowEntity follow : follows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("添加员工", follow.getWecomUserId());
            item.put("客户备注", follow.getRemark());
            item.put("客户描述", follow.getDescription());
            item.put("渠道参数", follow.getState());
            item.put("添加方式", describeAddWay(follow.getAddWay()));
            item.put("添加方式编码", follow.getAddWay());
            item.put("添加时间", follow.getContactCreatedAt());
            item.put("备注企业", follow.getRemarkCorpName());
            item.put("备注手机号", parseJsonValue(follow.getMobilesJson()));
            item.put("标签", formatTags(follow.getTagsJson(), corpTags));
            followData.add(item);
        }
        root.put("好友关系", followData);

        List<Map<String, Object>> groupData = new ArrayList<Map<String, Object>>();
        for (WecomGroupMemberEntity membership : memberships) {
            WecomGroupChatEntity group =
                    dataService.getGroup(tenantId, config.getId(), membership.getGroupChatId());
            if (group == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("客户群ID", group.getChatId());
            item.put("客户群名称", group.getName());
            item.put("群主", group.getOwnerUserId());
            item.put("群状态", group.getChatStatus());
            item.put("群创建时间", group.getGroupCreatedAt());
            item.put("入群时间", membership.getJoinedAt());
            item.put("入群方式", describeJoinScene(membership.getJoinScene()));
            item.put("入群方式编码", membership.getJoinScene());
            item.put("邀请人", membership.getInviterUserId());
            item.put("群昵称", membership.getGroupNickname());
            item.put("群公告", group.getNotice());
            JSONObject groupDetail = groupDetails == null ? null : groupDetails.get(group.getChatId());
            item.put("群管理员", resolveGroupAdmins(groupDetail));
            item.put("当前群成员数", resolveGroupMemberCount(groupDetail));
            groupData.add(item);
        }
        root.put("客户群关系", groupData);
        JSONObject contactDetail = contactDetails == null ? null : contactDetails.get(contact.getExternalUserId());
        if (contactDetail != null) {
            root.put("详情补拉状态", "已补充客户详情");
        }
        return JSON.toJSONString(root);
    }

    private Map<String, Object> buildSyncSummary(
            WecomExternalContactEntity contact,
            List<WecomContactFollowEntity> follows,
            List<WecomGroupMemberEntity> memberships) {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("好友关系数", follows == null ? 0 : follows.size());
        summary.put("所在客户群数", memberships == null ? 0 : memberships.size());
        summary.put("优先联系人", contact == null ? null : contact.getName());
        LocalDateTime latestContactAt = null;
        if (follows != null) {
            for (WecomContactFollowEntity follow : follows) {
                if (follow.getContactCreatedAt() != null
                        && (latestContactAt == null || follow.getContactCreatedAt().isAfter(latestContactAt))) {
                    latestContactAt = follow.getContactCreatedAt();
                }
            }
        }
        LocalDateTime latestJoinedAt = null;
        if (memberships != null) {
            for (WecomGroupMemberEntity membership : memberships) {
                if (membership.getJoinedAt() != null
                        && (latestJoinedAt == null || membership.getJoinedAt().isAfter(latestJoinedAt))) {
                    latestJoinedAt = membership.getJoinedAt();
                }
            }
        }
        summary.put("最近添加时间", latestContactAt);
        summary.put("最近入群时间", latestJoinedAt);
        return summary;
    }

    private List<Map<String, Object>> parseExternalProfile(WecomExternalContactEntity contact) {
        JSONObject profile = parseProfile(contact);
        if (profile == null) {
            return Collections.emptyList();
        }
        JSONObject externalProfile = profile.getJSONObject("external_profile");
        JSONArray attrs = externalProfile == null ? null : externalProfile.getJSONArray("external_attr");
        if (attrs == null || attrs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < attrs.size(); index++) {
            JSONObject attr = attrs.getJSONObject(index);
            Map<String, Object> item = formatExternalAttribute(attr);
            if (!item.isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }

    private Map<String, Object> formatExternalAttribute(JSONObject attr) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        if (attr == null) {
            return item;
        }
        item.put("资料名称", attr.getString("name"));
        Integer type = attr.getInteger("type");
        item.put("资料类型", describeExternalAttributeType(type));
        if (Integer.valueOf(0).equals(type)) {
            JSONObject text = attr.getJSONObject("text");
            item.put("资料内容", text == null ? null : text.getString("value"));
        } else if (Integer.valueOf(1).equals(type)) {
            JSONObject web = attr.getJSONObject("web");
            item.put("网页标题", web == null ? null : web.getString("title"));
            item.put("网页地址", web == null ? null : web.getString("url"));
        } else if (Integer.valueOf(2).equals(type)) {
            JSONObject miniprogram = attr.getJSONObject("miniprogram");
            item.put("小程序标题", miniprogram == null ? null : miniprogram.getString("title"));
            item.put("小程序AppId", miniprogram == null ? null : miniprogram.getString("appid"));
            item.put("小程序路径", miniprogram == null ? null : miniprogram.getString("pagepath"));
        } else {
            item.put("原始资料", attr);
        }
        return item;
    }

    private String describeExternalAttributeType(Integer type) {
        if (Integer.valueOf(0).equals(type)) {
            return "文本";
        }
        if (Integer.valueOf(1).equals(type)) {
            return "网页";
        }
        if (Integer.valueOf(2).equals(type)) {
            return "小程序";
        }
        return type == null ? null : "类型" + type;
    }

    private List<Map<String, Object>> formatTags(
            String tagsJson, Map<String, Map<String, Object>> corpTags) {
        Object value = parseJsonValue(tagsJson);
        if (value == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> tags = new ArrayList<Map<String, Object>>();
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.size(); index++) {
                appendTag(tags, array.get(index), corpTags);
            }
        } else {
            appendTag(tags, value, corpTags);
        }
        return tags;
    }

    private void appendTag(
            List<Map<String, Object>> tags,
            Object value,
            Map<String, Map<String, Object>> corpTags) {
        if (value == null) {
            return;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String tagId = firstText(object.getString("tag_id"), object.getString("id"));
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("标签名称", firstText(object.getString("tag_name"), object.getString("name")));
            item.put("标签分组", object.getString("group_name"));
            item.put("标签ID", tagId);
            item.put("标签类型", object.getInteger("type"));
            mergeTagInfo(item, tagId, corpTags);
            tags.add(item);
            return;
        }
        String tagId = String.valueOf(value);
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("标签ID", tagId);
        mergeTagInfo(item, tagId, corpTags);
        tags.add(item);
    }

    private void mergeTagInfo(
            Map<String, Object> item,
            String tagId,
            Map<String, Map<String, Object>> corpTags) {
        if (!StringUtils.hasText(tagId) || corpTags == null || !corpTags.containsKey(tagId)) {
            return;
        }
        Map<String, Object> tag = corpTags.get(tagId);
        if (!StringUtils.hasText((String) item.get("标签名称"))) {
            item.put("标签名称", tag.get("标签名称"));
        }
        if (!StringUtils.hasText((String) item.get("标签分组"))) {
            item.put("标签分组", tag.get("标签分组"));
        }
    }

    private List<String> resolveGroupAdmins(JSONObject groupDetail) {
        JSONObject group = groupDetail == null ? null : groupDetail;
        JSONArray adminList = group == null ? null : group.getJSONArray("admin_list");
        if (adminList == null || adminList.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> admins = new ArrayList<String>();
        for (int index = 0; index < adminList.size(); index++) {
            JSONObject admin = adminList.getJSONObject(index);
            String userId = admin == null ? null : admin.getString("userid");
            if (StringUtils.hasText(userId)) {
                admins.add(userId);
            }
        }
        return admins;
    }

    private Integer resolveGroupMemberCount(JSONObject groupDetail) {
        JSONObject group = groupDetail == null ? null : groupDetail;
        JSONArray members = group == null ? null : group.getJSONArray("member_list");
        return members == null ? null : Integer.valueOf(members.size());
    }

    private String describeContactType(Integer type) {
        if (Integer.valueOf(1).equals(type)) {
            return "微信用户";
        }
        if (Integer.valueOf(2).equals(type)) {
            return "企业微信用户";
        }
        return type == null ? null : "类型" + type;
    }

    private String describeAddWay(Integer addWay) {
        if (addWay == null) {
            return null;
        }
        if (Integer.valueOf(0).equals(addWay)) {
            return "未知来源";
        }
        if (Integer.valueOf(1).equals(addWay)) {
            return "扫描二维码";
        }
        if (Integer.valueOf(2).equals(addWay)) {
            return "搜索手机号";
        }
        if (Integer.valueOf(3).equals(addWay)) {
            return "名片分享";
        }
        if (Integer.valueOf(4).equals(addWay)) {
            return "群聊";
        }
        if (Integer.valueOf(5).equals(addWay)) {
            return "手机通讯录";
        }
        if (Integer.valueOf(6).equals(addWay)) {
            return "微信联系人";
        }
        if (Integer.valueOf(7).equals(addWay)) {
            return "微信添加好友申请";
        }
        if (Integer.valueOf(8).equals(addWay)) {
            return "安装第三方应用自动添加";
        }
        if (Integer.valueOf(9).equals(addWay)) {
            return "搜索邮箱";
        }
        if (Integer.valueOf(201).equals(addWay)) {
            return "内部成员共享";
        }
        if (Integer.valueOf(202).equals(addWay)) {
            return "管理员或负责人分配";
        }
        return "来源" + addWay;
    }

    private String describeJoinScene(Integer joinScene) {
        if (joinScene == null) {
            return null;
        }
        if (Integer.valueOf(1).equals(joinScene)) {
            return "邀请入群";
        }
        if (Integer.valueOf(2).equals(joinScene)) {
            return "扫码入群";
        }
        return "入群方式" + joinScene;
    }

    private Long resolveOwner(
            Long tenantId,
            WecomCorpConfigEntity config,
            List<WecomContactFollowEntity> follows,
            List<WecomGroupMemberEntity> memberships) {
        for (WecomContactFollowEntity follow : follows) {
            if (follow.getOwnerId() != null) {
                return follow.getOwnerId();
            }
        }
        for (WecomGroupMemberEntity membership : memberships) {
            WecomGroupChatEntity group =
                    dataService.getGroup(tenantId, config.getId(), membership.getGroupChatId());
            if (group == null) {
                continue;
            }
            Long ownerId = dataService.resolveOwnerId(
                    tenantId, config.getId(), group.getOwnerUserId(), config.getDefaultOwnerId());
            if (ownerId != null) {
                return ownerId;
            }
        }
        return config.getDefaultOwnerId();
    }

    private String resolveCompanyName(WecomExternalContactEntity contact, List<WecomContactFollowEntity> follows) {
        String companyName = firstText(contact.getCorpFullName(), contact.getCorpName());
        if (StringUtils.hasText(companyName)) {
            return companyName;
        }
        if (follows != null) {
            for (WecomContactFollowEntity follow : follows) {
                companyName = firstText(follow.getRemarkCorpName());
                if (StringUtils.hasText(companyName)) {
                    return companyName;
                }
            }
        }
        return firstExternalProfileValue(contact, "公司", "企业", "单位");
    }

    private String resolvePhone(List<WecomContactFollowEntity> follows, WecomExternalContactEntity contact) {
        if (follows != null) {
            for (WecomContactFollowEntity follow : follows) {
                if (!StringUtils.hasText(follow.getMobilesJson())) {
                    continue;
                }
                try {
                    JSONArray mobiles = JSON.parseArray(follow.getMobilesJson());
                    if (mobiles != null && !mobiles.isEmpty() && StringUtils.hasText(mobiles.getString(0))) {
                        return mobiles.getString(0);
                    }
                } catch (RuntimeException ex) {
                    LOGGER.warn("企业微信客户备注手机号解析失败，关系编号：{}", follow.getId());
                }
            }
        }
        return firstExternalProfileValue(contact, "手机", "电话", "联系方式", "联系电话");
    }

    private String resolveEmail(WecomExternalContactEntity contact) {
        return firstExternalProfileValue(contact, "邮箱", "邮件", "email", "mail");
    }

    private String firstExternalProfileValue(WecomExternalContactEntity contact, String... keywords) {
        JSONObject profile = parseProfile(contact);
        if (profile == null) {
            return null;
        }
        JSONObject externalProfile = profile.getJSONObject("external_profile");
        JSONArray attrs = externalProfile == null ? null : externalProfile.getJSONArray("external_attr");
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }
        for (int index = 0; index < attrs.size(); index++) {
            JSONObject attr = attrs.getJSONObject(index);
            if (attr == null || !matchesAny(attr.getString("name"), keywords)) {
                continue;
            }
            Integer type = attr.getInteger("type");
            if (Integer.valueOf(0).equals(type)) {
                JSONObject text = attr.getJSONObject("text");
                String value = text == null ? null : text.getString("value");
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
            if (Integer.valueOf(1).equals(type)) {
                JSONObject web = attr.getJSONObject("web");
                String value = web == null ? null : firstText(web.getString("title"), web.getString("url"));
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private JSONObject parseProfile(WecomExternalContactEntity contact) {
        if (contact == null || !StringUtils.hasText(contact.getProfileJson())) {
            return null;
        }
        try {
            return JSON.parseObject(contact.getProfileJson());
        } catch (RuntimeException ex) {
            LOGGER.warn("企业微信客户资料解析失败，外部联系人：{}", contact.getExternalUserId());
            return null;
        }
    }

    private boolean matchesAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        String lowerText = text.toLowerCase();
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && lowerText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private Object parseJsonValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return JSON.parse(value);
        } catch (RuntimeException ex) {
            return value;
        }
    }

    private String resolveContactName(WecomExternalContactEntity contact) {
        return firstText(contact.getName(), contact.getCorpFullName(), contact.getCorpName(), contact.getExternalUserId());
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("企业微信同步内容摘要生成失败", ex);
        }
    }

    private void start(WecomSyncTaskEntity task, WecomCorpConfigEntity config) {
        LocalDateTime now = DateTimes.now();
        task.setStatus(WecomSyncStatus.RUNNING);
        task.setStartedAt(now);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        config.setLastSyncStatus(WecomSyncStatus.RUNNING.name());
        config.setLastSyncAt(now);
        config.setLastError(null);
        config.setUpdatedAt(now);
        configMapper.updateById(config);
    }

    private void success(WecomSyncTaskEntity task, WecomCorpConfigEntity config) {
        LocalDateTime now = DateTimes.now();
        task.setStatus(WecomSyncStatus.SUCCESS);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        config.setLastSyncStatus(WecomSyncStatus.SUCCESS.name());
        config.setLastSyncAt(now);
        config.setLastSuccessAt(now);
        config.setLastError(null);
        config.setUpdatedAt(now);
        configMapper.updateById(config);
    }

    private void skip(WecomSyncTaskEntity task, WecomCorpConfigEntity config, String message) {
        LocalDateTime now = DateTimes.now();
        task.setStatus(WecomSyncStatus.SKIPPED);
        task.setErrorMessage(message);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        if (config != null) {
            config.setLastSyncStatus(WecomSyncStatus.SKIPPED.name());
            config.setLastSyncAt(now);
            config.setLastError(message);
            config.setUpdatedAt(now);
            configMapper.updateById(config);
        }
    }

    private void fail(WecomSyncTaskEntity task, WecomCorpConfigEntity config, String message) {
        LocalDateTime now = DateTimes.now();
        task.setStatus(WecomSyncStatus.FAILED);
        task.setErrorMessage(message);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        if (config != null) {
            config.setLastSyncStatus(WecomSyncStatus.FAILED.name());
            config.setLastSyncAt(now);
            config.setLastError(message);
            config.setUpdatedAt(now);
            configMapper.updateById(config);
        }
    }

    private String safeMessage(Exception ex) {
        if (ex == null || !StringUtils.hasText(ex.getMessage())) {
            return "企业微信同步失败";
        }
        String value = ex.getMessage().trim();
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
