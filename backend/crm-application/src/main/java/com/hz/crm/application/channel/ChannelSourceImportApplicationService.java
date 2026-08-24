package com.hz.crm.application.channel;

import com.alibaba.fastjson2.JSON;
import com.hz.crm.application.channel.dto.ChannelSourceImportRow;
import com.hz.crm.application.channel.dto.ChannelSourceResponse;
import com.hz.crm.application.channel.dto.ChannelSourceSyncResult;
import com.hz.crm.application.channel.dto.ExternalChannelSyncRequest;
import com.hz.crm.application.channel.dto.ExternalChannelSyncResult;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.domain.channel.ChannelSyncLogEntity;
import com.hz.crm.domain.channel.ChannelSyncTrigger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ChannelSourceImportApplicationService {

    private static final String EXTERNAL_PROVIDER = "WECOM_SMART_SHEET_EXPORT";

    @Autowired
    private ChannelSourceApplicationService channelSourceApplicationService;

    @Autowired
    private ChannelApplicationService channelApplicationService;

    public ChannelSourceSyncResult importRows(
            Long tenantId,
            Long operatorId,
            String sourceUrl,
            List<ChannelSourceImportRow> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("CHANNEL_SOURCE_IMPORT_001", "导出文件中没有可导入的数据");
        }
        ChannelSourceResponse source = channelSourceApplicationService.resolveBySourceUrl(tenantId, sourceUrl);
        if (source.getProductId() == null) {
            throw new BusinessException("CHANNEL_SOURCE_IMPORT_013", "请先在渠道配置中关联产品后再导入数据");
        }
        ChannelSyncLogEntity syncLog = channelSourceApplicationService.startLog(
                tenantId, source.getId(), ChannelSyncTrigger.MANUAL);
        ChannelSourceSyncResult result = new ChannelSourceSyncResult();
        result.setSourceId(source.getId());
        result.setLogId(syncLog.getId());
        result.setFetchedCount(rows.size());
        for (ChannelSourceImportRow row : rows) {
            try {
                ExternalChannelSyncResult syncResult = channelApplicationService.syncExternalChannel(
                        tenantId, buildSyncRequest(source, row));
                if (syncResult.isCreated()) {
                    result.setCreatedCount(result.getCreatedCount() + 1);
                } else if (syncResult.isUpdated()) {
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                }
            } catch (RuntimeException ex) {
                result.setFailedCount(result.getFailedCount() + 1);
                log.warn("企微智能表格行数据导入失败，来源编号：{}，行号：{}",
                        source.getId(), Integer.valueOf(row.getRowNumber()), ex);
            }
        }
        return channelSourceApplicationService.completeLog(
                tenantId, source.getId(), syncLog.getId(), result, buildFieldSnapshot(rows));
    }

    private ExternalChannelSyncRequest buildSyncRequest(
            ChannelSourceResponse source,
            ChannelSourceImportRow row) {
        String snapshot = JSON.toJSONString(row.getValues());
        ExternalChannelSyncRequest request = new ExternalChannelSyncRequest();
        request.setExternalProvider(EXTERNAL_PROVIDER);
        request.setExternalKey(source.getId() + ":" + sha256(buildIdentity(row)));
        request.setExternalVersion(sha256(snapshot));
        request.setTitle(limitText(
                firstText(row.getContactName(), row.getCompanyName(), "企微智能表格记录"), 128));
        request.setSource("WECHAT");
        request.setContactName(limitText(row.getContactName(), 128));
        request.setCompanyName(limitText(row.getCompanyName(), 128));
        request.setPhone(limitText(row.getPhone(), 32));
        request.setEmail(limitText(row.getEmail(), 128));
        request.setProductId(source.getProductId());
        request.setRemark(buildStructuredRemark(row.getValues()));
        request.setOccurredAt(row.getSubmittedAt());
        request.setSourceSnapshot(snapshot);
        return request;
    }

    private String buildIdentity(ChannelSourceImportRow row) {
        String phone = normalize(row.getPhone());
        if (phone != null) {
            return "phone:" + phone;
        }
        String email = normalize(row.getEmail());
        if (email != null) {
            return "email:" + email;
        }
        String company = normalize(row.getCompanyName());
        String contact = normalize(row.getContactName());
        if (company != null || contact != null) {
            return "person:" + safeText(company) + ":" + safeText(contact);
        }
        return "row:" + JSON.toJSONString(row.getValues());
    }

    private String buildFieldSnapshot(List<ChannelSourceImportRow> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0).getValues() == null) {
            return "[]";
        }
        List<String> fields = new ArrayList<String>(rows.get(0).getValues().keySet());
        Collections.sort(fields);
        return JSON.toJSONString(fields);
    }

    private String buildStructuredRemark(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("### 表格填写信息\n");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = trimToNull(entry.getValue());
            if (value != null) {
                builder.append("- ").append(entry.getKey()).append("：").append(value).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", Integer.valueOf(item & 0xff)));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("渠道数据指纹生成失败", ex);
        }
    }

    private String normalize(String value) {
        String text = trimToNull(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String firstText(String first, String second) {
        String value = trimToNull(first);
        return value == null ? trimToNull(second) : value;
    }

    private String firstText(String first, String second, String third) {
        String value = firstText(first, second);
        return value == null ? trimToNull(third) : value;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String limitText(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
