package com.sakurafubuki.yume.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class CacheExpiry(val millis: Long?) {
    HOUR_1(60 * 60 * 1000L),
    HOUR_6(6  * 60 * 60 * 1000L),
    HOUR_12(12 * 60 * 60 * 1000L),
    DAY_1(24 * 60 * 60 * 1000L),
    DAY_3(3  * 24 * 60 * 60 * 1000L),
    WEEK_1(7  * 24 * 60 * 60 * 1000L),
    MONTH_1(30 * 24 * 60 * 60 * 1000L),
    NEVER(null),
    ;
}
