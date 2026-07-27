package com.hz.crm.agent.runtime.workflow;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentWorkflowDefinition {

    private String code;

    private String name;

    private List<AgentWorkflowNode> nodes = new ArrayList<AgentWorkflowNode>();

    public AgentWorkflowDefinition addNode(AgentWorkflowNode node) {
        this.nodes.add(node);
        return this;
    }
}
