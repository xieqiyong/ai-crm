package com.hz.crm.mcp.support;

import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

public abstract class CrmMcpToolSupport {

    protected CrmMcpContext resolveContext(String tenantId, String userId, String dataScope) {
        CrmMcpContext context = new CrmMcpContext();
        context.setTenantId(requiredId(tenantId, "租户编号"));
        context.setUserId(requiredId(userId, "用户编号"));
        context.setDataScope(normalizeDataScope(dataScope));
        return context;
    }

    protected int pageNo(Integer value) {
        if (value == null || value.intValue() < 1) {
            return 1;
        }
        return value.intValue();
    }

    protected int pageSize(Integer value) {
        if (value == null || value.intValue() < 1) {
            return 10;
        }
        if (value.intValue() > 50) {
            return 50;
        }
        return value.intValue();
    }

    protected Long requiredId(String value, String fieldName) {
        Long id = optionalId(value, fieldName);
        if (id == null) {
            throw new BusinessException("MCP_PARAM_001", fieldName + "不能为空");
        }
        return id;
    }

    protected Long optionalId(String value, String fieldName) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new BusinessException("MCP_PARAM_003", fieldName + "格式不正确");
        }
    }

    protected <E extends Enum<E>> E optionalEnum(String value, Class<E> enumType, String fieldName) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("MCP_PARAM_002", fieldName + "取值不正确");
        }
    }

    protected <T> Map<String, Object> pageResult(PageData<T> page, List<Map<String, Object>> records) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mode", "PAGE");
        result.put("total", Long.valueOf(page == null ? 0L : page.getTotal()));
        result.put("pageNo", Integer.valueOf(page == null ? 1 : page.getPageNo()));
        result.put("pageSize", Integer.valueOf(page == null ? 10 : page.getPageSize()));
        result.put("records", records == null ? new ArrayList<Map<String, Object>>() : records);
        result.put("usageRule", "只能基于返回的真实CRM数据回答，禁止补全或编造未返回的信息");
        return result;
    }

    protected Map<String, Object> detailResult(Map<String, Object> record) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mode", "DETAIL");
        result.put("record", record == null ? new LinkedHashMap<String, Object>() : record);
        result.put("usageRule", "只能基于返回的真实CRM数据回答，禁止补全或编造未返回的信息");
        return result;
    }

    protected Map<String, Object> overviewResult(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mode", "OVERVIEW");
        result.put("data", data == null ? new LinkedHashMap<String, Object>() : data);
        result.put("usageRule", "只能基于返回的真实CRM数据回答，禁止补全或编造未返回的信息");
        return result;
    }

    protected String id(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    protected String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    protected String date(LocalDate value) {
        return value == null ? null : String.valueOf(value);
    }

    protected String dateTime(LocalDateTime value) {
        return value == null ? null : String.valueOf(value);
    }

    protected String plainText(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        String cleaned = text
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
        return shrink(cleaned, maxLength);
    }

    protected String shrink(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    protected String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeDataScope(String value) {
        String scope = trimToNull(value);
        if (scope == null) {
            return "SELF";
        }
        String normalized = scope.toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return "ALL";
        }
        return "SELF";
    }
}
