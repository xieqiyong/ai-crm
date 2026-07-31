package com.hz.crm.agent.web.tool;

import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolCallParam;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

abstract class CrmQueryToolSupport {

    private AgentRuntimeRequest runtimeRequest;

    protected void bindRuntimeRequest(AgentRuntimeRequest request) {
        this.runtimeRequest = request;
    }

    protected AgentRuntimeRequest runtimeRequest() {
        return runtimeRequest;
    }

    protected ToolResultBlock validateRuntime(String... requiredPermissions) {
        if (runtimeRequest == null
                || runtimeRequest.getTenantId() == null
                || runtimeRequest.getUserId() == null) {
            return ToolResultBlock.error("CRM查询工具缺少租户或用户上下文");
        }
        if (!hasAnyPermission(requiredPermissions)) {
            return ToolResultBlock.error("当前用户没有使用该CRM查询工具的权限");
        }
        return null;
    }

    protected Long tenantId() {
        return runtimeRequest.getTenantId();
    }

    protected Long userId() {
        return runtimeRequest.getUserId();
    }

    protected String dataScope() {
        Object value = context().get("dataScope");
        String scope = text(value).toUpperCase(Locale.ROOT);
        if ("ALL".equals(scope)
                || "DEPARTMENT".equals(scope)
                || "DEPARTMENT_AND_CHILD".equals(scope)
                || "SELF".equals(scope)) {
            return scope;
        }
        return "SELF";
    }

    protected Map<String, Object> input(ToolCallParam param) {
        if (param == null || param.getInput() == null) {
            return new LinkedHashMap<String, Object>();
        }
        return param.getInput();
    }

    protected Map<String, Object> objectSchema(
            Map<String, Object> properties, String... requiredFields) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        List<String> required = values(requiredFields);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    protected Map<String, Object> stringField(String description) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "string");
        field.put("description", description);
        return field;
    }

    protected Map<String, Object> integerField(
            String description, int minimum, int maximum) {
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("type", "integer");
        field.put("description", description);
        field.put("minimum", Integer.valueOf(minimum));
        field.put("maximum", Integer.valueOf(maximum));
        return field;
    }

    protected int pageNo(Object value) {
        Integer parsed = integer(value);
        return parsed == null || parsed.intValue() < 1 ? 1 : parsed.intValue();
    }

    protected int pageSize(Object value) {
        Integer parsed = integer(value);
        if (parsed == null || parsed.intValue() < 1) {
            return 10;
        }
        return Math.min(parsed.intValue(), 20);
    }

    protected Long optionalId(Object value, String fieldName) {
        String id = text(value);
        if (id.length() == 0) {
            return null;
        }
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + "格式不正确");
        }
    }

    protected <E extends Enum<E>> E optionalEnum(
            Object value, Class<E> enumType, String fieldName) {
        String text = text(value);
        if (text.length() == 0) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + "取值不正确");
        }
    }

    protected JSONObject pageResult(
            long total, int pageNo, int pageSize, List<JSONObject> records) {
        JSONObject result = new JSONObject();
        result.put("mode", "PAGE");
        result.put("total", Long.valueOf(total));
        result.put("pageNo", Integer.valueOf(pageNo));
        result.put("pageSize", Integer.valueOf(pageSize));
        result.put("records", records == null ? new ArrayList<JSONObject>() : records);
        result.put("usageRule", "只根据返回的真实CRM数据回答，禁止补全或编造未返回的信息。");
        return result;
    }

    protected JSONObject detailResult(JSONObject record) {
        JSONObject result = new JSONObject();
        result.put("mode", "DETAIL");
        result.put("record", record);
        result.put("usageRule", "只根据返回的真实CRM数据回答，禁止补全或编造未返回的信息。");
        return result;
    }

    protected String id(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    protected String dateTime(TemporalAccessor value) {
        return value == null ? null : String.valueOf(value);
    }

    protected String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    protected String shrink(String value, int maxLength) {
        String normalized = normalize(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    protected String plainText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return shrink(text, maxLength);
    }

    private Map<String, Object> context() {
        if (runtimeRequest == null || runtimeRequest.getContext() == null) {
            return new LinkedHashMap<String, Object>();
        }
        return runtimeRequest.getContext();
    }

    private boolean hasAnyPermission(String... requiredPermissions) {
        List<String> permissions = permissions();
        if (permissions.contains("*")) {
            return true;
        }
        if (requiredPermissions == null || requiredPermissions.length == 0) {
            return true;
        }
        for (String requiredPermission : requiredPermissions) {
            if (permissions.contains(requiredPermission)) {
                return true;
            }
        }
        return false;
    }

    private List<String> permissions() {
        Object value = context().get("permissions");
        List<String> permissions = new ArrayList<String>();
        if (value instanceof Collection<?>) {
            for (Object item : (Collection<?>) value) {
                String permission = text(item);
                if (permission.length() > 0) {
                    permissions.add(permission);
                }
            }
            return permissions;
        }
        String text = text(value);
        if (text.length() == 0) {
            return permissions;
        }
        for (String permission : text.split(",")) {
            String normalized = permission.trim();
            if (normalized.length() > 0) {
                permissions.add(normalized);
            }
        }
        return permissions;
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> values(String... values) {
        List<String> list = new ArrayList<String>();
        if (values == null) {
            return list;
        }
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                list.add(value);
            }
        }
        return list;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }
}
