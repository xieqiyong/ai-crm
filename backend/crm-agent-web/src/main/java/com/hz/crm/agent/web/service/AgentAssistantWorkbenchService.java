package com.hz.crm.agent.web.service;

import com.hz.crm.agent.web.dto.AgentAssistantAgentResponse;
import com.hz.crm.agent.web.dto.AgentAssistantConversationActionRequest;
import com.hz.crm.agent.web.dto.AgentAssistantConversationListRequest;
import com.hz.crm.agent.web.dto.AgentAssistantConversationResponse;
import com.hz.crm.agent.web.dto.AgentAssistantMessageResponse;
import com.hz.crm.agent.web.dto.AgentAssistantMessagesRequest;
import com.hz.crm.agent.web.dto.AgentAssistantRunRequest;
import com.hz.crm.agent.web.dto.AgentAssistantRunStopRequest;
import com.hz.crm.common.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentAssistantWorkbenchService {

    public List<AgentAssistantAgentResponse> agents(Long tenantId, Long userId) {
        throw migrated();
    }

    public List<AgentAssistantConversationResponse> conversations(
            Long tenantId,
            Long userId,
            AgentAssistantConversationListRequest request) {
        throw migrated();
    }

    public List<AgentAssistantMessageResponse> messages(
            Long tenantId,
            Long userId,
            AgentAssistantMessagesRequest request) {
        throw migrated();
    }

    public void deleteConversation(
            Long tenantId,
            Long userId,
            AgentAssistantConversationActionRequest request) {
        throw migrated();
    }

    public SseEmitter runStream(
            Long tenantId,
            Long userId,
            AgentAssistantRunRequest request) {
        throw migrated();
    }

    public boolean stopRun(
            Long tenantId,
            Long userId,
            AgentAssistantRunStopRequest request) {
        throw migrated();
    }

    private BusinessException migrated() {
        return new BusinessException("AI_RUNTIME_MIGRATED", "AI智能体工作台已迁移到Python Runtime，请通过Gateway访问");
    }
}
