package com.hz.crm.web.channel;

import com.hz.crm.application.channel.ChannelSourceImportApplicationService;
import com.hz.crm.application.channel.dto.ChannelSourceImportRow;
import com.hz.crm.application.channel.dto.ChannelSourceSyncResult;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.exception.BusinessException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/internal/channel/source")
public class InternalChannelSourceController {

    @Autowired
    private ChannelSourceImportApplicationService channelSourceImportApplicationService;

    @Autowired
    private ChannelSourceExcelImportParser channelSourceExcelImportParser;

    @Value("${crm.channel.sync.tenant-id:0}")
    private Long tenantId;

    @Value("${crm.channel.sync.operator-id:0}")
    private Long operatorId;

    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<ChannelSourceSyncResult> importExcel(
            @RequestParam("sourceUrl") String sourceUrl,
            @RequestParam("file") MultipartFile file) {
        validateIdentity();
        List<ChannelSourceImportRow> rows = channelSourceExcelImportParser.parse(file);
        return ApiResult.ok(channelSourceImportApplicationService.importRows(
                tenantId, operatorId, sourceUrl, rows));
    }

    private void validateIdentity() {
        if (tenantId == null || tenantId.longValue() <= 0L) {
            throw new BusinessException("CHANNEL_SYNC_CONFIG_001", "渠道同步租户编号尚未配置");
        }
        if (operatorId == null || operatorId.longValue() <= 0L) {
            throw new BusinessException("CHANNEL_SYNC_CONFIG_002", "渠道同步操作人编号尚未配置");
        }
    }
}
