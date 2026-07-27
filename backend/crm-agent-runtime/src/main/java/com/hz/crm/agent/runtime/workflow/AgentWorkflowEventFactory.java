package com.hz.crm.agent.runtime.workflow;

import com.hz.crm.agent.runtime.core.AgentRuntimeRequest;
import com.hz.crm.agent.runtime.dto.AgentRuntimeEvent;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowEventFactory {

    public AgentRuntimeEvent success(AgentWorkflowNode node, AgentRuntimeRequest request) {
        AgentRuntimeEvent event = new AgentRuntimeEvent();
        event.setId(UUID.randomUUID().toString().replace("-", ""));
        event.setType("WORKFLOW_STEP");
        event.getMetadata().put("workflow", true);
        event.getMetadata().put("node", node.getCode());
        event.getMetadata().put("nodeName", node.getName());
        event.getMetadata().put("nodeType", node.getType());
        event.getMetadata().put("status", "SUCCESS");
        if (request != null) {
            event.getMetadata().put("sceneCode", request.getSceneCode());
            event.getMetadata().put("businessType", request.getBusinessType());
            event.getMetadata().put("businessId", request.getBusinessId());
        }
        return event;
    }
}
