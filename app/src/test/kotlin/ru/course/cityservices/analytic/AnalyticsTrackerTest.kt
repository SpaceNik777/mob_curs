package ru.course.cityservices.analytic

import org.junit.Test

// Аналитика пишет в android.util.Log, которого  не существует на JVM: тест падает с «Method d in android.util.Log not mocked».
// Компонент, зависящий от платформы, нельзя проверить юнит-тестом без обертки/интерфейса
class AnalyticsTrackerTest {

    @Test
    fun `событие успешно записывается`() {
        val tracker = AnalyticsTracker()

        tracker.track(event = "test_event")
    }
}
