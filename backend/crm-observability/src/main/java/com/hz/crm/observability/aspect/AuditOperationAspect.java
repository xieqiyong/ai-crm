package com.hz.crm.observability.aspect;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.audit.AuditOperation;
import com.hz.crm.common.audit.AuditPrincipal;
import com.hz.crm.observability.dto.AuditLogRecord;
import com.hz.crm.observability.service.AuditLogService;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditOperationAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditOperationAspect.class);

    @Autowired
    private AuditLogService auditLogService;

    @Around(value = "@annotation(auditOperation)", argNames = "joinPoint,auditOperation")
    public Object record(ProceedingJoinPoint joinPoint, AuditOperation auditOperation) throws Throwable {
        long startedAt = System.currentTimeMillis();
        Object result = null;
        Throwable thrown = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            thrown = ex;
            throw ex;
        } finally {
            writeSafely(joinPoint, auditOperation, result, thrown, startedAt);
        }
    }

    private void writeSafely(
            ProceedingJoinPoint joinPoint,
            AuditOperation operation,
            Object result,
            Throwable thrown,
            long startedAt) {
        try {
            AuditPrincipal principal = findPrincipal(joinPoint.getArgs());
            if (principal == null || principal.getTenantId() == null) {
                return;
            }
            AuditLogRecord record = new AuditLogRecord();
            record.setTenantId(principal.getTenantId());
            record.setOperatorId(principal.getUserId());
            record.setAction(operation.module() + ":" + operation.action());
            record.setTargetType(emptyToNull(operation.targetType()));
            record.setTargetId(resolveTargetId(joinPoint.getArgs(), result, operation));
            record.setDetailJson(buildDetail(
                    joinPoint, operation, principal, result, thrown, startedAt, record.getTargetId()));
            auditLogService.record(record);
        } catch (RuntimeException ex) {
            LOGGER.warn("审计日志写入失败", ex);
        }
    }

    private String buildDetail(
            ProceedingJoinPoint joinPoint,
            AuditOperation operation,
            AuditPrincipal principal,
            Object result,
            Throwable thrown,
            long startedAt,
            Long targetId) {
        JSONObject detail = new JSONObject();
        detail.put("module", operation.module());
        detail.put("action", operation.action());
        detail.put("description", operation.description());
        detail.put("operatorName", resolveOperatorName(principal));
        detail.put("success", thrown == null);
        detail.put("costMillis", System.currentTimeMillis() - startedAt);
        detail.put("traceId", MDC.get("traceId"));
        detail.put("method", joinPoint.getSignature().toLongString());
        detail.put("targetId", targetId);
        detail.put("targetName", resolveTargetName(joinPoint.getArgs(), result, operation));
        if (operation.recordParameters()) {
            detail.put("parameters", sanitizeParameters(joinPoint));
        }
        if (thrown != null) {
            detail.put("errorType", thrown.getClass().getSimpleName());
            detail.put("errorMessage", thrown.getMessage());
        }
        return JSON.toJSONString(detail);
    }

    private AuditPrincipal findPrincipal(Object[] arguments) {
        if (arguments == null) {
            return null;
        }
        for (Object argument : arguments) {
            if (argument instanceof AuditPrincipal) {
                return (AuditPrincipal) argument;
            }
        }
        return null;
    }

    private Long resolveTargetId(Object[] arguments, Object result, AuditOperation operation) {
        Object source = argumentAt(arguments, operation.targetArgument());
        Long targetId = toLong(readProperty(source, operation.targetIdField()));
        if (targetId != null) {
            return targetId;
        }
        Object responseData = unwrapResult(result);
        targetId = toLong(readProperty(responseData, operation.targetIdField()));
        if (targetId != null) {
            return targetId;
        }
        return toLong(readProperty(responseData, "id"));
    }

    private String resolveTargetName(Object[] arguments, Object result, AuditOperation operation) {
        Object source = argumentAt(arguments, operation.targetArgument());
        String name = readName(source);
        if (name != null) {
            return name;
        }
        return readName(unwrapResult(result));
    }

    private String readName(Object source) {
        String[] fields = {"name", "title", "displayName", "username", "companyName"};
        for (int i = 0; i < fields.length; i++) {
            Object value = readProperty(source, fields[i]);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private Object sanitizeParameters(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] arguments = joinPoint.getArgs();
        JSONObject parameters = new JSONObject();
        for (int i = 0; i < arguments.length; i++) {
            Object argument = arguments[i];
            if (skipParameter(argument)) {
                continue;
            }
            String name = names != null && names.length > i ? names[i] : "参数" + (i + 1);
            parameters.put(name, sanitizeValue(JSON.toJSON(argument)));
        }
        return parameters;
    }

    private boolean skipParameter(Object argument) {
        if (argument == null || argument instanceof AuditPrincipal || argument instanceof byte[]) {
            return true;
        }
        String className = argument.getClass().getName();
        return className.contains("MultipartFile")
                || className.contains("ServletRequest")
                || className.contains("ServletResponse")
                || className.contains("BindingResult");
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof JSONObject) {
            JSONObject source = (JSONObject) value;
            JSONObject target = new JSONObject();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (isSensitive(entry.getKey())) {
                    target.put(entry.getKey(), "******");
                } else {
                    target.put(entry.getKey(), sanitizeValue(entry.getValue()));
                }
            }
            return target;
        }
        if (value instanceof JSONArray) {
            JSONArray source = (JSONArray) value;
            JSONArray target = new JSONArray();
            for (Object item : source) {
                target.add(sanitizeValue(item));
            }
            return target;
        }
        if (value instanceof Map) {
            JSONObject target = new JSONObject();
            Iterator<? extends Map.Entry<?, ?>> iterator = ((Map<?, ?>) value).entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                String key = String.valueOf(entry.getKey());
                target.put(key, isSensitive(key) ? "******" : sanitizeValue(entry.getValue()));
            }
            return target;
        }
        if (value instanceof Collection) {
            JSONArray target = new JSONArray();
            for (Object item : (Collection<?>) value) {
                target.add(sanitizeValue(item));
            }
            return target;
        }
        return value;
    }

    private boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.replace("_", "").replace("-", "").toLowerCase();
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("apikey")
                || normalized.contains("accesstoken")
                || normalized.contains("refreshtoken")
                || normalized.contains("authorization")
                || normalized.contains("credential");
    }

    private Object argumentAt(Object[] arguments, int index) {
        if (arguments == null || index < 0 || index >= arguments.length) {
            return null;
        }
        return arguments[index];
    }

    private Object unwrapResult(Object result) {
        if (result instanceof ApiResult) {
            return ((ApiResult<?>) result).getData();
        }
        return result;
    }

    private Object readProperty(Object source, String fieldName) {
        if (source == null || fieldName == null || fieldName.trim().length() == 0) {
            return null;
        }
        if (source instanceof Map) {
            return ((Map<?, ?>) source).get(fieldName);
        }
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        Object value = invokeGetter(source, "get" + suffix);
        if (value != null) {
            return value;
        }
        return invokeGetter(source, "is" + suffix);
    }

    private Object invokeGetter(Object source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            return method.invoke(source);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null || String.valueOf(value).trim().length() == 0) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveOperatorName(AuditPrincipal principal) {
        if (principal.getDisplayName() != null && principal.getDisplayName().trim().length() > 0) {
            return principal.getDisplayName().trim();
        }
        return principal.getUsername();
    }

    private String emptyToNull(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }
}
