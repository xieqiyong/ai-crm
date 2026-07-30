package com.hz.crm.wecom.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.common.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WecomApiClient {

    @Value("${crm.wecom.base-url:https://qyapi.weixin.qq.com}")
    private String baseUrl;

    @Value("${crm.wecom.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${crm.wecom.read-timeout-ms:30000}")
    private int readTimeoutMs;

    public String getAccessToken(String corpId, String corpSecret) {
        String path = "/cgi-bin/gettoken?corpid=" + encode(corpId) + "&corpsecret=" + encode(corpSecret);
        JSONObject response = execute("GET", path, null);
        String token = response.getString("access_token");
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("WECOM_002", "企业微信未返回访问令牌");
        }
        return token;
    }

    public List<String> listFollowUsers(String accessToken) {
        JSONObject response = execute(
                "GET",
                "/cgi-bin/externalcontact/get_follow_user_list?access_token=" + encode(accessToken),
                null);
        JSONArray array = response.getJSONArray("follow_user");
        List<String> users = new ArrayList<String>();
        if (array == null) {
            return users;
        }
        for (int index = 0; index < array.size(); index++) {
            Object value = array.get(index);
            String userId;
            if (value instanceof JSONObject) {
                userId = ((JSONObject) value).getString("userid");
            } else {
                userId = value == null ? null : String.valueOf(value);
            }
            if (StringUtils.hasText(userId) && !users.contains(userId)) {
                users.add(userId);
            }
        }
        return users;
    }

    public List<JSONObject> listExternalContacts(String accessToken, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<JSONObject> records = new ArrayList<JSONObject>();
        for (int start = 0; start < userIds.size(); start += 100) {
            int end = Math.min(start + 100, userIds.size());
            String cursor = "";
            do {
                JSONObject body = new JSONObject();
                body.put("userid_list", new ArrayList<String>(userIds.subList(start, end)));
                body.put("limit", 100);
                if (StringUtils.hasText(cursor)) {
                    body.put("cursor", cursor);
                }
                JSONObject response = execute(
                        "POST",
                        "/cgi-bin/externalcontact/batch/get_by_user?access_token=" + encode(accessToken),
                        body);
                JSONArray array = response.getJSONArray("external_contact_list");
                appendObjects(records, array);
                cursor = response.getString("next_cursor");
            } while (StringUtils.hasText(cursor));
        }
        return records;
    }

    public List<String> listGroupChatIds(String accessToken, List<String> ownerUserIds) {
        if (ownerUserIds == null || ownerUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> chatIds = new ArrayList<String>();
        for (int start = 0; start < ownerUserIds.size(); start += 100) {
            int end = Math.min(start + 100, ownerUserIds.size());
            String cursor = "";
            do {
                JSONObject body = new JSONObject();
                body.put("status_filter", 0);
                body.put("limit", 100);
                JSONObject ownerFilter = new JSONObject();
                ownerFilter.put("userid_list", new ArrayList<String>(ownerUserIds.subList(start, end)));
                body.put("owner_filter", ownerFilter);
                if (StringUtils.hasText(cursor)) {
                    body.put("cursor", cursor);
                }
                JSONObject response = execute(
                        "POST",
                        "/cgi-bin/externalcontact/groupchat/list?access_token=" + encode(accessToken),
                        body);
                JSONArray array = response.getJSONArray("group_chat_list");
                if (array != null) {
                    for (int index = 0; index < array.size(); index++) {
                        JSONObject item = array.getJSONObject(index);
                        String chatId = item == null ? null : item.getString("chat_id");
                        if (StringUtils.hasText(chatId) && !chatIds.contains(chatId)) {
                            chatIds.add(chatId);
                        }
                    }
                }
                cursor = response.getString("next_cursor");
            } while (StringUtils.hasText(cursor));
        }
        return chatIds;
    }

    public JSONObject getGroupChat(String accessToken, String chatId) {
        JSONObject body = new JSONObject();
        body.put("chat_id", chatId);
        body.put("need_name", 1);
        JSONObject response = execute(
                "POST",
                "/cgi-bin/externalcontact/groupchat/get?access_token=" + encode(accessToken),
                body);
        JSONObject groupChat = response.getJSONObject("group_chat");
        if (groupChat == null) {
            throw new BusinessException("WECOM_003", "企业微信未返回客户群详情");
        }
        return groupChat;
    }

    private JSONObject execute(String method, String path, JSONObject body) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(normalizeBaseUrl() + path).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                byte[] bytes = JSON.toJSONBytes(body);
                OutputStream outputStream = connection.getOutputStream();
                try {
                    outputStream.write(bytes);
                    outputStream.flush();
                } finally {
                    outputStream.close();
                }
            }
            int status = connection.getResponseCode();
            InputStream inputStream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String text = readText(inputStream);
            JSONObject response = JSON.parseObject(text);
            if (response == null) {
                throw new BusinessException("WECOM_001", "企业微信接口返回为空");
            }
            Integer errorCode = response.getInteger("errcode");
            if (errorCode != null && errorCode.intValue() != 0) {
                throw new BusinessException(
                        "WECOM_" + errorCode,
                        "企业微信接口调用失败：" + response.getString("errmsg"));
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("WECOM_001", "企业微信接口调用失败：" + ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void appendObjects(List<JSONObject> records, JSONArray array) {
        if (array == null) {
            return;
        }
        for (int index = 0; index < array.size(); index++) {
            JSONObject item = array.getJSONObject(index);
            if (item != null) {
                records.add(item);
            }
        }
    }

    private String readText(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, length);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            inputStream.close();
        }
    }

    private String normalizeBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception ex) {
            return "";
        }
    }
}
