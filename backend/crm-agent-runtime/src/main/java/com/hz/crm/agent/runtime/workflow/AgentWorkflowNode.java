package com.hz.crm.agent.runtime.workflow;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentWorkflowNode {

    private String code;

    private String name;

    private String type;

    public static AgentWorkflowNode of(String code, String name, String type) {
        AgentWorkflowNode node = new AgentWorkflowNode();
        node.setCode(code);
        node.setName(name);
        node.setType(type);
        return node;
    }
}
