package ru.course.cityservices.analytic

import android.util.Log

// Аналитика пишется в android.util.Log, поэтому её невозможно
// вызвать из JVM-теста (см. AnalyticsTrackerTest - он падает с «Method d in android.util.Log not mocked»)
class AnalyticsTracker {

    private val buffer = mutableListOf<Pair<String, Long>>()

    fun track(event: String, params: Map<String, String> = emptyMap()) {
        Log.d(TAG, "$event $params")

        buffer += event to System.currentTimeMillis()
    }

    fun recentEvents(): List<Pair<String, Long>> = buffer.toList()

    private companion object {

        const val TAG = "CityAnalytics"
    }
}
