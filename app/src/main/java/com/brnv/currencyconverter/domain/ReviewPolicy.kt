package com.brnv.currencyconverter.domain

import java.time.Duration

data class ReviewUsage(
    val firstUse: Long,
    val sessions: Int,
    val conversions: Int,
    val foregroundMillis: Long,
    val lastRequest: Long,
)

object ReviewPolicy {
    fun eligible(usage: ReviewUsage, now: Long): Boolean =
        now - usage.firstUse >= Duration.ofDays(3).toMillis() &&
            usage.sessions >= 3 && usage.conversions >= 10 &&
            usage.foregroundMillis >= Duration.ofMinutes(3).toMillis() &&
            (usage.lastRequest == 0L || now - usage.lastRequest >= Duration.ofDays(90).toMillis())
}
