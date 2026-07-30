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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
        syncContacts(task, config, accessToken, followUsers);
        syncGroups(task, config, accessToken, followUsers);
        syncChannels(task, config);
    }

    private void syncContacts(
            WecomSyncTaskEntity task,
            WecomCorpConfigEntity config,
            String accessToken,
            List<String> followUsers) {
        List<JSONObject> records = apiClient.listExternalContacts(accessToken, followUsers);
        task.setContactsFetched(records.size());
        for (JSONObject record : records) {
            WecomUpsertResult<WecomExternalContactEntity> contactResult =
                    dataService.upsertContact(task.getTenantId(), config.getId(), record);
            WecomExternalContactEntity contact = contactResult.getData();
            if (contact == null) {
                continue;
            }
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
    }

    private void syncGroups(
            WecomSyncTaskEntity task,
            WecomCorpConfigEntity config,
            String accessToken,
            List<String> followUsers) {
        List<String> chatIds = apiClient.listGroupChatIds(accessToken, followUsers);
        task.setGroupsFetched(chatIds.size());
        for (String chatId : chatIds) {
            JSONObject group = apiClient.getGroupChat(accessToken, chatId);
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
    }

    private void syncChannels(WecomSyncTaskEntity task, WecomCorpConfigEntity config) {
        List<WecomExternalContactEntity> contacts =
                dataService.listContacts(task.getTenantId(), config.getId());
        task.setContactsFetched(contacts.size());
        for (WecomExternalContactEntity contact : contacts) {
            List<WecomContactFollowEntity> follows =
                    dataService.listFollows(task.getTenantId(), config.getId(), contact.getExternalUserId());
            List<WecomGroupMemberEntity> memberships =
                    dataService.listMemberships(task.getTenantId(), config.getId(), contact.getExternalUserId());
            String snapshot = buildSnapshot(task.getTenantId(), config, contact, follows, memberships);
            ExternalChannelSyncRequest request = new ExternalChannelSyncRequest();
            request.setExternalProvider("WECOM");
            request.setExternalKey(config.getCorpId() + ":" + contact.getExternalUserId());
            request.setExternalVersion(sha256(snapshot));
            request.setTitle("企业微信客户 · " + resolveContactName(contact));
            request.setSource(memberships.isEmpty() ? "WECHAT" : "WECHAT_GROUP");
            request.setContactName(contact.getName());
            request.setCompanyName(firstText(contact.getCorpFullName(), contact.getCorpName()));
            request.setPhone(resolvePhone(follows));
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
            List<WecomGroupMemberEntity> memberships) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("数据来源", "企业微信客户联系主动同步");
        root.put("企业ID", config.getCorpId());
        Map<String, Object> contactData = new LinkedHashMap<String, Object>();
        contactData.put("外部联系人ID", contact.getExternalUserId());
        contactData.put("姓名", contact.getName());
        contactData.put("企业名称", firstText(contact.getCorpFullName(), contact.getCorpName()));
        contactData.put("职位", contact.getPosition());
        contactData.put("性别", contact.getGender());
        contactData.put("联合ID", contact.getUnionId());
        root.put("客户基础信息", contactData);

        List<Map<String, Object>> followData = new ArrayList<Map<String, Object>>();
        for (WecomContactFollowEntity follow : follows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("添加员工", follow.getWecomUserId());
            item.put("客户备注", follow.getRemark());
            item.put("客户描述", follow.getDescription());
            item.put("渠道参数", follow.getState());
            item.put("添加方式", follow.getAddWay());
            item.put("添加时间", follow.getContactCreatedAt());
            item.put("备注企业", follow.getRemarkCorpName());
            item.put("备注手机号", parseJsonValue(follow.getMobilesJson()));
            item.put("标签", parseJsonValue(follow.getTagsJson()));
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
            item.put("入群时间", membership.getJoinedAt());
            item.put("入群方式", membership.getJoinScene());
            item.put("邀请人", membership.getInviterUserId());
            item.put("群昵称", membership.getGroupNickname());
            item.put("群公告", group.getNotice());
            groupData.add(item);
        }
        root.put("客户群关系", groupData);
        return JSON.toJSONString(root);
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

    private String resolvePhone(List<WecomContactFollowEntity> follows) {
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
        return null;
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
