package com.hz.crm.wecom.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.domain.wecom.WecomContactFollowEntity;
import com.hz.crm.domain.wecom.WecomExternalContactEntity;
import com.hz.crm.domain.wecom.WecomGroupChatEntity;
import com.hz.crm.domain.wecom.WecomGroupMemberEntity;
import com.hz.crm.domain.wecom.WecomUserBindingEntity;
import com.hz.crm.domain.wecom.mapper.WecomContactFollowMapper;
import com.hz.crm.domain.wecom.mapper.WecomExternalContactMapper;
import com.hz.crm.domain.wecom.mapper.WecomGroupChatMapper;
import com.hz.crm.domain.wecom.mapper.WecomGroupMemberMapper;
import com.hz.crm.domain.wecom.mapper.WecomUserBindingMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WecomDataService {

    @Autowired
    private WecomExternalContactMapper contactMapper;

    @Autowired
    private WecomContactFollowMapper followMapper;

    @Autowired
    private WecomGroupChatMapper groupChatMapper;

    @Autowired
    private WecomGroupMemberMapper groupMemberMapper;

    @Autowired
    private WecomUserBindingMapper bindingMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public void ensureBindings(Long tenantId, Long configId, List<String> userIds) {
        if (userIds == null) {
            return;
        }
        LocalDateTime now = DateTimes.now();
        for (String userId : userIds) {
            if (!StringUtils.hasText(userId) || findBinding(tenantId, configId, userId) != null) {
                continue;
            }
            WecomUserBindingEntity entity = new WecomUserBindingEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setConfigId(configId);
            entity.setWecomUserId(userId);
            entity.setWecomUserName(userId);
            entity.setEnabled(true);
            entity.setDeleted(false);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            bindingMapper.insert(entity);
        }
    }

    @Transactional
    public WecomUpsertResult<WecomExternalContactEntity> upsertContact(
            Long tenantId, Long configId, JSONObject profile) {
        JSONObject external = profile == null ? null : profile.getJSONObject("external_contact");
        if (external == null) {
            external = profile;
        }
        String externalUserId = external == null ? null : external.getString("external_userid");
        if (!StringUtils.hasText(externalUserId)) {
            return emptyResult();
        }
        WecomExternalContactEntity entity = findContact(tenantId, configId, externalUserId);
        boolean created = entity == null;
        LocalDateTime now = DateTimes.now();
        if (created) {
            entity = new WecomExternalContactEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setConfigId(configId);
            entity.setExternalUserId(externalUserId);
            entity.setFirstSyncedAt(now);
            entity.setCreatedAt(now);
            entity.setDeleted(false);
        }
        entity.setName(external.getString("name"));
        entity.setContactType(external.getInteger("type"));
        entity.setGender(external.getInteger("gender"));
        entity.setAvatar(external.getString("avatar"));
        entity.setPosition(external.getString("position"));
        entity.setCorpName(external.getString("corp_name"));
        entity.setCorpFullName(external.getString("corp_full_name"));
        entity.setUnionId(external.getString("unionid"));
        entity.setProfileJson(JSON.toJSONString(external));
        entity.setActive(true);
        entity.setLastSyncedAt(now);
        entity.setUpdatedAt(now);
        if (created) {
            contactMapper.insert(entity);
        } else {
            contactMapper.updateById(entity);
        }
        WecomUpsertResult<WecomExternalContactEntity> result =
                new WecomUpsertResult<WecomExternalContactEntity>();
        result.setData(entity);
        result.setCreated(created);
        return result;
    }

    @Transactional
    public WecomUpsertResult<WecomContactFollowEntity> upsertFollow(
            Long tenantId,
            Long configId,
            Long externalContactId,
            String externalUserId,
            JSONObject followInfo,
            Long defaultOwnerId) {
        if (followInfo == null || !StringUtils.hasText(followInfo.getString("userid"))) {
            return emptyResult();
        }
        String wecomUserId = followInfo.getString("userid");
        WecomContactFollowEntity entity = findFollow(tenantId, configId, externalUserId, wecomUserId);
        boolean created = entity == null;
        LocalDateTime now = DateTimes.now();
        if (created) {
            entity = new WecomContactFollowEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setConfigId(configId);
            entity.setExternalContactId(externalContactId);
            entity.setExternalUserId(externalUserId);
            entity.setWecomUserId(wecomUserId);
            entity.setFirstSyncedAt(now);
            entity.setCreatedAt(now);
            entity.setDeleted(false);
        }
        entity.setOwnerId(resolveOwnerId(tenantId, configId, wecomUserId, defaultOwnerId));
        entity.setRemark(followInfo.getString("remark"));
        entity.setDescription(followInfo.getString("description"));
        entity.setState(followInfo.getString("state"));
        entity.setAddWay(followInfo.getInteger("add_way"));
        entity.setContactCreatedAt(fromEpochSeconds(followInfo.getLong("createtime")));
        entity.setRemarkCorpName(followInfo.getString("remark_corp_name"));
        entity.setMobilesJson(toJson(followInfo.get("remark_mobiles")));
        entity.setTagsJson(toJson(firstValue(followInfo, "tags", "tag_id")));
        entity.setActive(true);
        entity.setLastSyncedAt(now);
        entity.setUpdatedAt(now);
        if (created) {
            followMapper.insert(entity);
        } else {
            followMapper.updateById(entity);
        }
        WecomUpsertResult<WecomContactFollowEntity> result =
                new WecomUpsertResult<WecomContactFollowEntity>();
        result.setData(entity);
        result.setCreated(created);
        return result;
    }

    @Transactional
    public WecomUpsertResult<WecomGroupChatEntity> upsertGroup(
            Long tenantId, Long configId, JSONObject group) {
        String chatId = group == null ? null : group.getString("chat_id");
        if (!StringUtils.hasText(chatId)) {
            return emptyResult();
        }
        WecomGroupChatEntity entity = findGroup(tenantId, configId, chatId);
        boolean created = entity == null;
        LocalDateTime now = DateTimes.now();
        if (created) {
            entity = new WecomGroupChatEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setConfigId(configId);
            entity.setChatId(chatId);
            entity.setCreatedAt(now);
            entity.setDeleted(false);
        }
        entity.setName(group.getString("name"));
        entity.setOwnerUserId(group.getString("owner"));
        entity.setNotice(group.getString("notice"));
        entity.setChatStatus(group.getInteger("status"));
        entity.setGroupCreatedAt(fromEpochSeconds(group.getLong("create_time")));
        entity.setActive(true);
        entity.setLastSyncedAt(now);
        entity.setUpdatedAt(now);
        if (created) {
            groupChatMapper.insert(entity);
        } else {
            groupChatMapper.updateById(entity);
        }
        WecomUpsertResult<WecomGroupChatEntity> result =
                new WecomUpsertResult<WecomGroupChatEntity>();
        result.setData(entity);
        result.setCreated(created);
        return result;
    }

    @Transactional
    public WecomUpsertResult<WecomGroupMemberEntity> upsertGroupMember(
            Long tenantId,
            Long configId,
            Long groupChatId,
            String chatId,
            JSONObject member) {
        String memberUserId = member == null ? null : member.getString("userid");
        if (!StringUtils.hasText(memberUserId)) {
            return emptyResult();
        }
        WecomGroupMemberEntity entity = findGroupMember(tenantId, configId, chatId, memberUserId);
        boolean created = entity == null;
        LocalDateTime now = DateTimes.now();
        if (created) {
            entity = new WecomGroupMemberEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setConfigId(configId);
            entity.setGroupChatId(groupChatId);
            entity.setChatId(chatId);
            entity.setMemberUserId(memberUserId);
            entity.setFirstSyncedAt(now);
            entity.setCreatedAt(now);
            entity.setDeleted(false);
        }
        entity.setMemberType(member.getInteger("type"));
        entity.setName(member.getString("name"));
        entity.setUnionId(member.getString("unionid"));
        entity.setJoinedAt(fromEpochSeconds(member.getLong("join_time")));
        entity.setJoinScene(member.getInteger("join_scene"));
        JSONObject inviter = member.getJSONObject("invitor");
        entity.setInviterUserId(inviter == null ? null : inviter.getString("userid"));
        entity.setGroupNickname(member.getString("group_nickname"));
        entity.setActive(true);
        entity.setLastSyncedAt(now);
        entity.setUpdatedAt(now);
        if (created) {
            groupMemberMapper.insert(entity);
        } else {
            groupMemberMapper.updateById(entity);
        }
        WecomUpsertResult<WecomGroupMemberEntity> result =
                new WecomUpsertResult<WecomGroupMemberEntity>();
        result.setData(entity);
        result.setCreated(created);
        return result;
    }

    @Transactional(readOnly = true)
    public List<WecomExternalContactEntity> listContacts(Long tenantId, Long configId) {
        QueryWrapper<WecomExternalContactEntity> wrapper = new QueryWrapper<WecomExternalContactEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("deleted", false);
        wrapper.eq("active", true);
        return contactMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public List<WecomContactFollowEntity> listFollows(
            Long tenantId, Long configId, String externalUserId) {
        QueryWrapper<WecomContactFollowEntity> wrapper = new QueryWrapper<WecomContactFollowEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("external_user_id", externalUserId);
        wrapper.eq("deleted", false);
        wrapper.eq("active", true);
        wrapper.orderByAsc("created_at");
        return followMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public List<WecomGroupMemberEntity> listMemberships(
            Long tenantId, Long configId, String externalUserId) {
        QueryWrapper<WecomGroupMemberEntity> wrapper = new QueryWrapper<WecomGroupMemberEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("member_user_id", externalUserId);
        wrapper.eq("deleted", false);
        wrapper.eq("active", true);
        wrapper.orderByAsc("created_at");
        return groupMemberMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public WecomGroupChatEntity getGroup(Long tenantId, Long configId, Long groupId) {
        QueryWrapper<WecomGroupChatEntity> wrapper = new QueryWrapper<WecomGroupChatEntity>();
        wrapper.eq("id", groupId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("deleted", false);
        return groupChatMapper.selectOne(wrapper);
    }

    @Transactional(readOnly = true)
    public Long resolveOwnerId(Long tenantId, Long configId, String wecomUserId, Long defaultOwnerId) {
        WecomUserBindingEntity binding = findBinding(tenantId, configId, wecomUserId);
        if (binding != null && binding.isEnabled() && binding.getCrmUserId() != null) {
            return binding.getCrmUserId();
        }
        return defaultOwnerId;
    }

    private WecomExternalContactEntity findContact(Long tenantId, Long configId, String externalUserId) {
        QueryWrapper<WecomExternalContactEntity> wrapper = new QueryWrapper<WecomExternalContactEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("external_user_id", externalUserId);
        wrapper.eq("deleted", false);
        return contactMapper.selectOne(wrapper);
    }

    private WecomContactFollowEntity findFollow(
            Long tenantId, Long configId, String externalUserId, String wecomUserId) {
        QueryWrapper<WecomContactFollowEntity> wrapper = new QueryWrapper<WecomContactFollowEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("external_user_id", externalUserId);
        wrapper.eq("wecom_user_id", wecomUserId);
        wrapper.eq("deleted", false);
        return followMapper.selectOne(wrapper);
    }

    private WecomGroupChatEntity findGroup(Long tenantId, Long configId, String chatId) {
        QueryWrapper<WecomGroupChatEntity> wrapper = new QueryWrapper<WecomGroupChatEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("chat_id", chatId);
        wrapper.eq("deleted", false);
        return groupChatMapper.selectOne(wrapper);
    }

    private WecomGroupMemberEntity findGroupMember(
            Long tenantId, Long configId, String chatId, String memberUserId) {
        QueryWrapper<WecomGroupMemberEntity> wrapper = new QueryWrapper<WecomGroupMemberEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("chat_id", chatId);
        wrapper.eq("member_user_id", memberUserId);
        wrapper.eq("deleted", false);
        return groupMemberMapper.selectOne(wrapper);
    }

    private WecomUserBindingEntity findBinding(Long tenantId, Long configId, String wecomUserId) {
        if (!StringUtils.hasText(wecomUserId)) {
            return null;
        }
        QueryWrapper<WecomUserBindingEntity> wrapper = new QueryWrapper<WecomUserBindingEntity>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("config_id", configId);
        wrapper.eq("wecom_user_id", wecomUserId);
        wrapper.eq("deleted", false);
        return bindingMapper.selectOne(wrapper);
    }

    private LocalDateTime fromEpochSeconds(Long value) {
        if (value == null || value.longValue() <= 0L) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(value.longValue()), ZoneId.systemDefault());
    }

    private Object firstValue(JSONObject object, String firstKey, String secondKey) {
        Object value = object.get(firstKey);
        return value == null ? object.get(secondKey) : value;
    }

    private String toJson(Object value) {
        return value == null ? null : JSON.toJSONString(value);
    }

    private <T> WecomUpsertResult<T> emptyResult() {
        return new WecomUpsertResult<T>();
    }
}
