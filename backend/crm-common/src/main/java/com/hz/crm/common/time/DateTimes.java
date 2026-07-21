package com.hz.crm.common.time;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class DateTimes {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private DateTimes() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(DEFAULT_ZONE);
    }
}
