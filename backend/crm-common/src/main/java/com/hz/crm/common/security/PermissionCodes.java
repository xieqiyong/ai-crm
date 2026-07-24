package com.hz.crm.common.security;

public final class PermissionCodes {

    public static final String ADMIN = "*";

    public static final String LEAD_VIEW = "crm:lead:view";

    public static final String LEAD_MANAGE = "crm:lead:manage";

    public static final String CUSTOMER_VIEW = "crm:customer:view";

    public static final String CUSTOMER_MANAGE = "crm:customer:manage";

    public static final String OPPORTUNITY_VIEW = "crm:opportunity:view";

    public static final String OPPORTUNITY_MANAGE = "crm:opportunity:manage";

    public static final String DASHBOARD_VIEW = "crm:dashboard:view";

    public static final String AGENT_VIEW = "crm:agent:view";

    public static final String AGENT_MANAGE = "crm:agent:manage";

    public static final String KNOWLEDGE_MANAGE = "crm:knowledge:manage";

    public static final String WORKFLOW_MANAGE = "crm:workflow:manage";

    public static final String OBSERVABILITY_VIEW = "crm:observability:view";

    private PermissionCodes() {
    }
}
