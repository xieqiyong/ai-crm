package com.hz.crm.web.media;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.media.MediaTranscriptionTaskEntity;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "crm.media.transcription.volcengine", name = "enabled", havingValue = "true")
public class VolcengineAsrClient {

    private static final Logger log = LoggerFactory.getLogger(VolcengineAsrClient.class);

    @Value("${crm.media.transcription.volcengine.base-url:https://openspeech.bytedance.com/api/v1/auc}")
    private String baseUrl;

    @Value("${crm.media.transcription.volcengine.appid:}")
    private String appid;

    @Value("${crm.media.transcription.volcengine.token:}")
    private String token;

    @Value("${crm.media.transcription.volcengine.cluster:}")
    private String cluster;

    @Value("${crm.media.transcription.volcengine.language:zh-CN}")
    private String language;

    @Value("${crm.media.transcription.volcengine.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${crm.media.transcription.public-url:}")
    private String publicUrl;

    public VolcengineAsrSubmitResult submit(MediaTranscriptionTaskEntity task) {
        validateConfig();
        long start = System.currentTimeMillis();
        String requestUrl = resolveUrl("/submit");
        log.info(
                "火山转写提交接口请求开始，taskId={}，followupId={}，requestUrl={}，audioFormat={}，audioUrl={}",
                task.getId(),
                task.getBusinessId(),
                requestUrl,
                resolveAudioFormat(task),
                shrink(StringUtils.hasText(task.getAudioFileUrl()) ? task.getAudioFileUrl() : task.getFileUrl()));
        try {
            String requestBody = buildSubmitBody(task);
            HttpURLConnection connection = openConnection(requestUrl);
            writeRequest(connection, requestBody);
            int statusCode = connection.getResponseCode();
            String responseText = readResponse(connection, statusCode);
            JSONObject response = parseResponse(statusCode, responseText);
            JSONObject data = resolveData(response);
            int code = resolveCode(response, data);
            if (code != 1000) {
                log.warn(
                        "火山转写提交接口业务失败，taskId={}，followupId={}，httpStatus={}，code={}，message={}，耗时={}ms",
                        task.getId(),
                        task.getBusinessId(),
                        Integer.valueOf(statusCode),
                        Integer.valueOf(code),
                        shrink(resolveMessage(response, data)),
                        Long.valueOf(System.currentTimeMillis() - start));
                throw new BusinessException("MEDIA_ASR_002", "火山转写提交失败：" + resolveMessage(response, data));
            }
            String taskId = firstText(data, "id", "task_id");
            if (!StringUtils.hasText(taskId)) {
                taskId = firstText(response, "id", "task_id");
            }
            if (!StringUtils.hasText(taskId)) {
                throw new BusinessException("MEDIA_ASR_003", "火山转写提交未返回任务编号");
            }
            VolcengineAsrSubmitResult result = new VolcengineAsrSubmitResult();
            result.setProviderTaskId(taskId);
            result.setRequestId(firstText(response, "request_id", "reqid"));
            result.setRawResultJson(response.toJSONString());
            log.info(
                    "火山转写提交接口请求完成，taskId={}，followupId={}，httpStatus={}，code={}，providerTaskId={}，requestId={}，responseLength={}，耗时={}ms",
                    task.getId(),
                    task.getBusinessId(),
                    Integer.valueOf(statusCode),
                    Integer.valueOf(code),
                    result.getProviderTaskId(),
                    result.getRequestId(),
                    Integer.valueOf(responseText == null ? 0 : responseText.length()),
                    Long.valueOf(System.currentTimeMillis() - start));
            return result;
        } catch (BusinessException ex) {
            log.warn(
                    "火山转写提交接口请求失败，taskId={}，followupId={}，message={}，耗时={}ms",
                    task.getId(),
                    task.getBusinessId(),
                    ex.getMessage(),
                    Long.valueOf(System.currentTimeMillis() - start));
            throw ex;
        } catch (Exception ex) {
            log.warn(
                    "火山转写提交接口请求异常，taskId={}，followupId={}，耗时={}ms",
                    task.getId(),
                    task.getBusinessId(),
                    Long.valueOf(System.currentTimeMillis() - start),
                    ex);
            throw new BusinessException("MEDIA_ASR_004", "火山转写提交异常：" + ex.getMessage());
        }
    }

    public VolcengineAsrQueryResult query(MediaTranscriptionTaskEntity task) {
        validateConfig();
        if (!StringUtils.hasText(task.getProviderTaskId())) {
            throw new BusinessException("MEDIA_ASR_005", "火山转写任务编号不能为空");
        }
        long start = System.currentTimeMillis();
        String requestUrl = resolveUrl("/query");
        log.info(
                "火山转写查询接口请求开始，taskId={}，followupId={}，providerTaskId={}，requestUrl={}",
                task.getId(),
                task.getBusinessId(),
                task.getProviderTaskId(),
                requestUrl);
        try {
            JSONObject body = new JSONObject();
            body.put("appid", appid);
            body.put("token", token);
            body.put("cluster", cluster);
            body.put("id", task.getProviderTaskId());
            HttpURLConnection connection = openConnection(requestUrl);
            writeRequest(connection, JSON.toJSONString(body));
            int statusCode = connection.getResponseCode();
            String responseText = readResponse(connection, statusCode);
            JSONObject response = parseResponse(statusCode, responseText);
            JSONObject data = resolveData(response);
            int code = resolveCode(response, data);
            VolcengineAsrQueryResult result = new VolcengineAsrQueryResult();
            result.setRawResultJson(response.toJSONString());
            if (code == 1000) {
                result.setFinished(true);
                result.setTranscriptText(resolveTranscript(response, data));
                JSONArray utterances = resolveUtterances(response, data);
                result.setUtterancesJson(utterances == null ? null : utterances.toJSONString());
                log.info(
                        "火山转写查询接口返回完成，taskId={}，followupId={}，providerTaskId={}，httpStatus={}，code={}，textLength={}，utterancesCount={}，responseLength={}，耗时={}ms",
                        task.getId(),
                        task.getBusinessId(),
                        task.getProviderTaskId(),
                        Integer.valueOf(statusCode),
                        Integer.valueOf(code),
                        Integer.valueOf(StringUtils.hasText(result.getTranscriptText()) ? result.getTranscriptText().length() : 0),
                        Integer.valueOf(utterances == null ? 0 : utterances.size()),
                        Integer.valueOf(responseText == null ? 0 : responseText.length()),
                        Long.valueOf(System.currentTimeMillis() - start));
                return result;
            }
            if (code == 2000 || code == 2001) {
                result.setProcessing(true);
                log.info(
                        "火山转写查询接口返回处理中，taskId={}，followupId={}，providerTaskId={}，httpStatus={}，code={}，responseLength={}，耗时={}ms",
                        task.getId(),
                        task.getBusinessId(),
                        task.getProviderTaskId(),
                        Integer.valueOf(statusCode),
                        Integer.valueOf(code),
                        Integer.valueOf(responseText == null ? 0 : responseText.length()),
                        Long.valueOf(System.currentTimeMillis() - start));
                return result;
            }
            result.setErrorMessage("火山转写查询失败：" + resolveMessage(response, data));
            log.warn(
                    "火山转写查询接口业务失败，taskId={}，followupId={}，providerTaskId={}，httpStatus={}，code={}，message={}，耗时={}ms",
                    task.getId(),
                    task.getBusinessId(),
                    task.getProviderTaskId(),
                    Integer.valueOf(statusCode),
                    Integer.valueOf(code),
                    shrink(result.getErrorMessage()),
                    Long.valueOf(System.currentTimeMillis() - start));
            return result;
        } catch (BusinessException ex) {
            log.warn(
                    "火山转写查询接口请求失败，taskId={}，followupId={}，providerTaskId={}，message={}，耗时={}ms",
                    task.getId(),
                    task.getBusinessId(),
                    task.getProviderTaskId(),
                    ex.getMessage(),
                    Long.valueOf(System.currentTimeMillis() - start));
            throw ex;
        } catch (Exception ex) {
            log.warn(
                    "火山转写查询接口请求异常，taskId={}，followupId={}，providerTaskId={}，耗时={}ms",
                    task.getId(),
                    task.getBusinessId(),
                    task.getProviderTaskId(),
                    Long.valueOf(System.currentTimeMillis() - start),
                    ex);
            throw new BusinessException("MEDIA_ASR_006", "火山转写查询异常：" + ex.getMessage());
        }
    }

    private String buildSubmitBody(MediaTranscriptionTaskEntity task) {
        JSONObject body = new JSONObject();
        JSONObject app = new JSONObject();
        app.put("appid", appid);
        app.put("token", token);
        app.put("cluster", cluster);
        body.put("app", app);
        JSONObject user = new JSONObject();
        user.put("uid", resolveUserId(task));
        body.put("user", user);
        JSONObject audio = new JSONObject();
        audio.put("url", resolveAudioUrl(task));
        audio.put("format", resolveAudioFormat(task));
        body.put("audio", audio);
        JSONObject additions = new JSONObject();
        additions.put("language", StringUtils.hasText(task.getLanguage()) ? task.getLanguage() : language);
        additions.put("use_itn", "True");
        additions.put("use_punc", "True");
        additions.put("with_speaker_info", "True");
        body.put("additions", additions);
        return body.toJSONString();
    }

    private String resolveUserId(MediaTranscriptionTaskEntity task) {
        Long userId = task.getOwnerId() == null ? task.getCreatorId() : task.getOwnerId();
        return userId == null ? "crm" : String.valueOf(userId);
    }

    private String resolveAudioUrl(MediaTranscriptionTaskEntity task) {
        String url = StringUtils.hasText(task.getAudioFileUrl()) ? task.getAudioFileUrl() : task.getFileUrl();
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("MEDIA_ASR_007", "音频文件访问地址不能为空");
        }
        String value = url.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (!StringUtils.hasText(publicUrl)) {
            throw new BusinessException("MEDIA_ASR_008", "音频文件不是公网地址，请配置CRM_MEDIA_TRANSCRIPTION_PUBLIC_URL或MinIO公网地址");
        }
        String base = publicUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return base + value;
    }

    private String resolveAudioFormat(MediaTranscriptionTaskEntity task) {
        String format = StringUtils.hasText(task.getAudioFileFormat()) ? task.getAudioFileFormat() : task.getFileFormat();
        if (!StringUtils.hasText(format)) {
            return "wav";
        }
        String text = format.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith(".")) {
            text = text.substring(1);
        }
        return text;
    }

    private String resolveUrl(String path) {
        String value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://openspeech.bytedance.com/api/v1/auc";
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value + path;
    }

    private void validateConfig() {
        if (!StringUtils.hasText(appid) || !StringUtils.hasText(token) || !StringUtils.hasText(cluster)) {
            throw new BusinessException("MEDIA_ASR_001", "火山语音转写配置不完整");
        }
    }

    private HttpURLConnection openConnection(String urlValue) throws Exception {
        URL url = new URL(urlValue);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer; " + token);
        return connection;
    }

    private void writeRequest(HttpURLConnection connection, String requestBody) throws Exception {
        byte[] requestBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        OutputStream outputStream = connection.getOutputStream();
        try {
            outputStream.write(requestBytes);
        } finally {
            outputStream.close();
        }
    }

    private String readResponse(HttpURLConnection connection, int statusCode) throws Exception {
        InputStream inputStream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (inputStream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        try {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }

    private JSONObject parseResponse(int statusCode, String responseText) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException("MEDIA_ASR_009", "火山接口调用失败：" + shrink(responseText));
        }
        try {
            return JSON.parseObject(responseText);
        } catch (Exception ex) {
            throw new BusinessException("MEDIA_ASR_010", "火山接口返回格式异常：" + shrink(responseText));
        }
    }

    private JSONObject resolveData(JSONObject response) {
        JSONObject data = response.getJSONObject("resp");
        if (data != null) {
            return data;
        }
        data = response.getJSONObject("data");
        return data == null ? response : data;
    }

    private int resolveCode(JSONObject response, JSONObject data) {
        Integer code = response.getInteger("code");
        if (code == null && data != null) {
            code = data.getInteger("code");
        }
        return code == null ? -1 : code.intValue();
    }

    private String resolveMessage(JSONObject response, JSONObject data) {
        String message = firstText(data, "message", "msg");
        if (StringUtils.hasText(message)) {
            return message;
        }
        message = firstText(response, "message", "msg");
        return StringUtils.hasText(message) ? message : response.toJSONString();
    }

    private String resolveTranscript(JSONObject response, JSONObject data) {
        String text = firstText(data, "text", "result");
        if (StringUtils.hasText(text)) {
            return text;
        }
        text = firstText(response, "text", "result");
        if (StringUtils.hasText(text)) {
            return text;
        }
        return buildTranscriptFromUtterances(resolveUtterances(response, data));
    }

    private JSONArray resolveUtterances(JSONObject response, JSONObject data) {
        JSONArray utterances = data == null ? null : data.getJSONArray("utterances");
        if (utterances != null) {
            return utterances;
        }
        return response.getJSONArray("utterances");
    }

    private String firstText(JSONObject object, String firstKey, String secondKey) {
        if (object == null) {
            return null;
        }
        String value = object.getString(firstKey);
        if (StringUtils.hasText(value)) {
            return value;
        }
        return object.getString(secondKey);
    }

    private String buildTranscriptFromUtterances(JSONArray utterances) {
        if (utterances == null || utterances.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < utterances.size(); index++) {
            JSONObject item = utterances.getJSONObject(index);
            String text = firstText(item, "text", "result");
            if (!StringUtils.hasText(text)) {
                text = firstText(item, "sentence", "utterance");
            }
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(text.trim());
        }
        return builder.toString();
    }

    private String shrink(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() > 800 ? text.substring(0, 800) : text;
    }
}
