package com.hz.crm.common.audit;

public interface AuditPrincipal {

    Long getTenantId();

    Long getUserId();

    String getUsername();

    String getDisplayName();
}
