package com.hz.crm.domain.channel;

public enum ChannelSource {
    WEBSITE,
    LANDING_PAGE,
    SMS,
    WECHAT,
    WECHAT_GROUP,
    PHONE,
    OFFLINE_EVENT,
    LIVE,
    REFERRAL,
    AD,
    OTHER;

    public static ChannelSource from(String value) {
        if (value == null || value.trim().length() == 0) {
            return OTHER;
        }
        String text = value.trim();
        for (ChannelSource source : values()) {
            if (source.name().equalsIgnoreCase(text)) {
                return source;
            }
        }
        return OTHER;
    }
}
