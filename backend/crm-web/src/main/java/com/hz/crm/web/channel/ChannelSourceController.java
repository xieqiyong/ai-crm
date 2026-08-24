package com.hz.crm.web.channel;

import com.hz.crm.application.channel.ChannelSourceApplicationService;
import com.hz.crm.application.channel.ChannelSourceImportApplicationService;
import com.hz.crm.application.channel.dto.ChannelSourceQuery;
import com.hz.crm.application.channel.dto.ChannelSourceImportRow;
import com.hz.crm.application.channel.dto.ChannelSourceResponse;
import com.hz.crm.application.channel.dto.ChannelSourceSaveRequest;
import com.hz.crm.application.channel.dto.ChannelSourceSyncRequest;
import com.hz.crm.application.channel.dto.ChannelSourceSyncResult;
import com.hz.crm.application.channel.dto.ChannelSyncLogResponse;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.audit.AuditOperation;
import com.hz.crm.web.support.IdRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/channel/source")
public class ChannelSourceController {

    @Autowired
    private ChannelSourceApplicationService channelSourceApplicationService;

    @Autowired
    private ChannelSourceImportApplicationService channelSourceImportApplicationService;

    @Autowired
    private ChannelSourceExcelImportParser channelSourceExcelImportParser;

    @PostMapping("/list")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:view')")
    public ApiResult<List<ChannelSourceResponse>> list(
            @RequestBody(required = false) ChannelSourceQuery query, JwtPrincipal principal) {
        return ApiResult.ok(channelSourceApplicationService.list(principal.getTenantId(), query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:view')")
    public ApiResult<ChannelSourceResponse> detail(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(channelSourceApplicationService.detail(principal.getTenantId(), request.getId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:manage')")
    @AuditOperation(
            module = "CHANNEL_SOURCE",
            action = "SAVE",
            description = "保存渠道来源配置",
            targetType = "CHANNEL_SOURCE",
            recordParameters = false)
    public ApiResult<ChannelSourceResponse> save(
            @Valid @RequestBody ChannelSourceSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(channelSourceApplicationService.save(
                principal.getTenantId(), principal.getUserId(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:manage')")
    @AuditOperation(
            module = "CHANNEL_SOURCE",
            action = "DELETE",
            description = "删除渠道来源配置",
            targetType = "CHANNEL_SOURCE")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        channelSourceApplicationService.delete(principal.getTenantId(), request.getId());
        return ApiResult.ok(null);
    }

    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:manage')")
    @AuditOperation(
            module = "CHANNEL_SOURCE",
            action = "IMPORT_EXCEL",
            description = "导入企微智能表格导出文件",
            targetType = "CHANNEL_SOURCE",
            recordParameters = false)
    public ApiResult<ChannelSourceSyncResult> importExcel(
            @RequestParam("sourceUrl") String sourceUrl,
            @RequestParam("file") MultipartFile file,
            JwtPrincipal principal) {
        List<ChannelSourceImportRow> rows = channelSourceExcelImportParser.parse(file);
        return ApiResult.ok(channelSourceImportApplicationService.importRows(
                principal.getTenantId(), principal.getUserId(), sourceUrl, rows));
    }

    @PostMapping("/logs")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:channel:view')")
    public ApiResult<List<ChannelSyncLogResponse>> logs(@RequestBody ChannelSourceSyncRequest request, JwtPrincipal principal) {
        return ApiResult.ok(channelSourceApplicationService.latestLogs(principal.getTenantId(), request.getId(), 10));
    }
}
