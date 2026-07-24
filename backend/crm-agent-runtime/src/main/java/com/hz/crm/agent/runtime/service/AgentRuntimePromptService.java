package com.hz.crm.agent.runtime.service;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimePromptService {

    @Autowired
    private AgentRuntimeProperties agentRuntimeProperties;

    public String render(String systemPrompt, String injectedPrompt, Map<String, Object> context) {
        StringBuilder builder = new StringBuilder();
        append(builder, agentRuntimeProperties.getBasePrompt());
        append(builder, systemPrompt);
        append(builder, injectedPrompt);
        return replaceContext(builder.toString(), safeContext(context));
    }

    private void append(StringBuilder builder, String text) {
        if (text == null || text.trim().length() == 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(text.trim());
    }

    private String replaceContext(String text, Map<String, Object> context) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private Map<String, Object> safeContext(Map<String, Object> context) {
        if (context == null) {
            return new HashMap<String, Object>();
        }
        return context;
    }
}
