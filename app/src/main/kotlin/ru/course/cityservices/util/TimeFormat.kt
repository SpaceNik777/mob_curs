package ru.course.cityservices.util

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Человекочитаемое "сколько времени прошло"
object TimeFormat {

    fun relative(
        instant: Instant,
        now: Instant = Clock.System.now(),
    ): String {
        val duration = now - instant

        return when {
            duration.isNegative() || duration < 1.minutes -> "только что"
            duration < 1.hours -> "${duration.inWholeMinutes} мин назад"
            duration < 1.days -> "${duration.inWholeHours} ч назад"
            duration < 2.days -> "вчера"

            else -> "${duration.inWholeDays} дн назад"
        }
    }
}
