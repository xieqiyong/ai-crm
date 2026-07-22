package com.hz.crm.domain.lead;

public enum LeadStatus {
    NEW,
    CONTACTED,
    FOLLOWING,
    QUALIFIED,
    NURTURING,
    CONVERTED,
    INVALID,
    DUPLICATE,
    CLOSED;

    public static LeadStatus recommended() {
        return NEW;
    }
}
