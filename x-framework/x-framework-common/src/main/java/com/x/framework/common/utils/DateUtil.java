package com.x.framework.common.utils;

import java.time.Instant;
import java.time.LocalDateTime;

/** Timestamp and local schedule-time conversion used by internal signing and AI schedules. */
public final class DateUtil {
    private DateUtil() {}

    public static Long getTimestamp() {
        return Instant.now().toEpochMilli();
    }

    public static LocalDateTime toLocalDateTime(Instant instant) {
        return cn.hutool.core.date.DateUtil.toLocalDateTime(instant);
    }
}
