package ru.course.cityservices.util

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

// Чистая функция с явным аргументом "сейчас" - время можно зафиксировать
class TimeFormatTest {

    @Test
    fun `меньше минуты - только что`() {
        assertEquals(
            "только что",
            TimeFormat.relative(
                Instant.fromEpochMilliseconds(1_000L),
                Instant.fromEpochMilliseconds(1_500L)
            )
        )
    }

    @Test
    fun `минуты назад`() {
        assertEquals(
            "5 мин назад",
            TimeFormat.relative(
                Instant.fromEpochMilliseconds(0L),
                Instant.fromEpochMilliseconds(5 * 60_000L)
            )
        )
    }

    @Test
    fun `часы назад`() {
        assertEquals(
            "3 ч назад",
            TimeFormat.relative(
                Instant.fromEpochMilliseconds(0L),
                Instant.fromEpochMilliseconds(3 * 3_600_000L)
            )
        )
    }

    @Test
    fun `ровно сутки - вчера`() {
        assertEquals(
            "вчера",
            TimeFormat.relative(
                Instant.fromEpochMilliseconds(0L),
                Instant.fromEpochMilliseconds(24 * 3_600_000L)
            )
        )
    }

    @Test
    fun `несколько дней назад`() {
        assertEquals(
            "4 дн назад",
            TimeFormat.relative(
                Instant.fromEpochMilliseconds(0L),
                Instant.fromEpochMilliseconds(4 * 24 * 3_600_000L)
            )
        )
    }
}
