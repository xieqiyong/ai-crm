package com.hz.crm.domain.customer;

public enum CustomerStatus {
    POTENTIAL,
    ACTIVE,
    DEALING,
    COOPERATED,
    SLEEPING,
    CHURNED,
    BLACKLIST;

    public static CustomerStatus recommended() {
        return POTENTIAL;
    }
}
