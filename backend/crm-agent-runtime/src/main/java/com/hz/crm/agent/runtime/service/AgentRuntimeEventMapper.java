package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import java.lang.reflect.Method;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeEventMapper {

    public AgentRuntimeEvent toRuntimeEvent(AgentEvent event) {
        AgentRuntimeEvent runtimeEvent = new AgentRuntimeEvent();
        runtimeEvent.setId(event.getId());
        runtimeEvent.setType(event.getType().name());
        runtimeEvent.getMetadata().put("source", event.getSource());
        runtimeEvent.getMetadata().put("createdAt", event.getCreatedAt());
        fillTokenUsage(runtimeEvent, event);
        if (event instanceof TextBlockDeltaEvent) {
            runtimeEvent.setContent(((TextBlockDeltaEvent) event).getDelta());
        } else if (event instanceof ToolCallStartEvent) {
            runtimeEvent.setToolName(((ToolCallStartEvent) event).getToolCallName());
        } else if (event instanceof ToolResultTextDeltaEvent) {
            runtimeEvent.setContent(((ToolResultTextDeltaEvent) event).getDelta());
        }
        return runtimeEvent;
    }

    private void fillTokenUsage(AgentRuntimeEvent runtimeEvent, AgentEvent event) {
        Object usage = readObject(event, "getUsage");
        if (usage == null) {
            usage = readObject(event, "getTokenUsage");
        }
        Long inputTokens = firstLong(
                readLong(event, "getInputTokens"),
                readLong(event, "getPromptTokens"),
                readLong(event, "getInputTokenCount"),
                readLong(usage, "getInputTokens"),
                readLong(usage, "getPromptTokens"),
                readLong(usage, "getInputTokenCount"));
        Long outputTokens = firstLong(
                readLong(event, "getOutputTokens"),
                readLong(event, "getCompletionTokens"),
                readLong(event, "getOutputTokenCount"),
                readLong(usage, "getOutputTokens"),
                readLong(usage, "getCompletionTokens"),
                readLong(usage, "getOutputTokenCount"));
        Long totalTokens = firstLong(
                readLong(event, "getTotalTokens"),
                readLong(event, "getTotalTokenCount"),
                readLong(usage, "getTotalTokens"),
                readLong(usage, "getTotalTokenCount"));
        putTokenValue(runtimeEvent, "inputTokens", inputTokens);
        putTokenValue(runtimeEvent, "outputTokens", outputTokens);
        putTokenValue(runtimeEvent, "totalTokens", totalTokens);
    }

    private void putTokenValue(AgentRuntimeEvent runtimeEvent, String key, Long value) {
        if (value != null && value.longValue() >= 0L) {
            runtimeEvent.getMetadata().put(key, value);
        }
    }

    private Long firstLong(Long first, Long second, Long third, Long fourth, Long fifth, Long sixth) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        if (third != null) {
            return third;
        }
        if (fourth != null) {
            return fourth;
        }
        if (fifth != null) {
            return fifth;
        }
        return sixth;
    }

    private Long firstLong(Long first, Long second, Long third, Long fourth) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        if (third != null) {
            return third;
        }
        return fourth;
    }

    private Object readObject(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ex) {
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Long readLong(Object target, String methodName) {
        Object value = readObject(target, methodName);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
