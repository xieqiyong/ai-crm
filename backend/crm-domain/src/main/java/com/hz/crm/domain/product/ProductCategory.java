package com.hz.crm.domain.product;

import lombok.Getter;

@Getter
public enum ProductCategory {

    AI_AGENT_PLATFORM("智能体平台"),

    INTELLIGENT_MARKETING("智能营销"),

    DATA_KNOWLEDGE("数据与知识库"),

    INDUSTRY_SOLUTION("行业解决方案"),

    IMPLEMENTATION_SERVICE("实施与技术服务"),

    OTHER("其他");

    private final String displayName;

    ProductCategory(String displayName) {
        this.displayName = displayName;
    }
}
