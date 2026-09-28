package com.hz.crm.application.system;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.application.system.dto.MessageWebhookSettingsResponse;
import com.hz.crm.common.exception.BusinessException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class MessageWebhookApplicationService {

    private static final Set<String> DINGTALK_HOSTS = new HashSet<String>(Arrays.asList(
            "oapi.dingtalk.com", "api.dingtalk.com"));

    private static final Set<String> FEISHU_HOSTS = new HashSet<String>(Arrays.asList(
            "open.feishu.cn", "open.larksuite.com"));

    @Autowired
    private SystemParameterApplicationService systemParameterApplicationService;

    @Autowired
    @Qualifier("messageWebhookTaskExecutor")
    private TaskExecutor messageWebhookTaskExecutor;

    public void notifyPublicPoolCreated(Long tenantId, int createdCount, String sourceName) {
        if (createdCount <= 0) {
            return;
        }
        MessageWebhookSettingsResponse settings =
                systemParameterApplicationService.messageWebhookSettings(tenantId);
        if (!settings.isEnabled() || !settings.isNotifyPublicPool()
                || !StringUtils.hasText(settings.getWebhookUrl())) {
            return;
        }
        final String content = buildPublicPoolMessage(createdCount, sourceName);
        messageWebhookTaskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    send(settings, content);
                    log.info("公海池新增Webhook通知发送完成，租户编号：{}，新增数量：{}",
                            tenantId, Integer.valueOf(createdCount));
                } catch (RuntimeException ex) {
                    log.warn("公海池新增Webhook通知发送失败，租户编号：{}，新增数量：{}",
                            tenantId, Integer.valueOf(createdCount), ex);
                }
            }
        });
    }

    public void test(Long tenantId) {
        MessageWebhookSettingsResponse settings =
                systemParameterApplicationService.messageWebhookSettings(tenantId);
        if (!StringUtils.hasText(settings.getWebhookUrl())) {
            throw new BusinessException("MESSAGE_WEBHOOK_001", "请先保存机器人Webhook地址");
        }
        send(settings, "CRM消息通知测试成功，机器人连接正常。\n测试时间："
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    private String buildPublicPoolMessage(int createdCount, String sourceName) {
        String source = StringUtils.hasText(sourceName) ? sourceName.trim() : "渠道数据同步";
        return "【CRM公海池提醒】\n"
                + source + "新增 " + createdCount + " 条待分配数据，请销售及时关注公海池。\n"
                + "进入时间："
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private void send(MessageWebhookSettingsResponse settings, String content) {
        URI uri = validateUri(settings.getPlatform(), settings.getWebhookUrl());
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] body = buildRequestBody(settings.getPlatform(), content)
                    .getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }
            int statusCode = connection.getResponseCode();
            String responseBody = readResponse(connection, statusCode);
            if (statusCode < 200 || statusCode >= 300) {
                throw new BusinessException("MESSAGE_WEBHOOK_002",
                        "机器人接口响应异常，状态码：" + statusCode);
            }
            verifyResponse(settings.getPlatform(), responseBody);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("MESSAGE_WEBHOOK_003", "机器人通知发送失败：" + ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private URI validateUri(String platform, String webhookUrl) {
        try {
            URI uri = URI.create(webhookUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new BusinessException("MESSAGE_WEBHOOK_004", "机器人Webhook必须使用有效的HTTPS地址");
            }
            String host = uri.getHost().toLowerCase();
            Set<String> allowedHosts = "FEISHU".equals(platform) ? FEISHU_HOSTS : DINGTALK_HOSTS;
            if (!allowedHosts.contains(host)) {
                throw new BusinessException("MESSAGE_WEBHOOK_005", "机器人Webhook域名与所选平台不匹配");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("MESSAGE_WEBHOOK_006", "机器人Webhook地址格式不正确");
        }
    }

    private String buildRequestBody(String platform, String content) {
        JSONObject body = new JSONObject();
        if ("FEISHU".equals(platform)) {
            JSONObject text = new JSONObject();
            text.put("text", content);
            body.put("msg_type", "text");
            body.put("content", text);
            return body.toJSONString();
        }
        JSONObject text = new JSONObject();
        text.put("content", content);
        body.put("msgtype", "text");
        body.put("text", text);
        return body.toJSONString();
    }

    private void verifyResponse(String platform, String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return;
        }
        JSONObject response = JSON.parseObject(responseBody);
        Integer code = "FEISHU".equals(platform)
                ? response.getInteger("code")
                : response.getInteger("errcode");
        if (code != null && code.intValue() != 0) {
            String message = "FEISHU".equals(platform)
                    ? response.getString("msg")
                    : response.getString("errmsg");
            throw new BusinessException("MESSAGE_WEBHOOK_007",
                    "机器人接口处理失败：" + (StringUtils.hasText(message) ? message : code));
        }
    }

    private String readResponse(HttpURLConnection connection, int statusCode) throws Exception {
        InputStream inputStream = statusCode >= 200 && statusCode < 400
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
