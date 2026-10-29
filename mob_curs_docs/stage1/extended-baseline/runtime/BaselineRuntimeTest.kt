package ru.course.cityservices.baselinefixture

import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import ru.course.cityservices.core.ServiceLocator as SL
import ru.course.cityservices.model.*
import ru.course.cityservices.network.*
import ru.course.cityservices.state.AppState
import ru.course.cityservices.storage.AppDatabase
import ru.course.cityservices.storage.SessionStore
import kotlin.time.Instant
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class BaselineRuntimeTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val result = JSONObject()
    private fun row(id: Long, user: Long = 1, status: String = "Создана") = RequestResponse(
        id, user, 1, status, "baseline-record-$id", "fixture address", Instant.fromEpochMilliseconds(1700000000000L + id))

    private class Api : CityApi {
        val rows = mutableListOf<RequestResponse>()
        var failBefore = false
        var timeoutAfter = false
        var creates = 0
        override suspend fun login(username: String, password: String) = AuthResponse(true, 1, "fixture")
        override suspend fun getServices() = emptyList<ServiceResponse>()
        override suspend fun getRequests(userId: Long): List<RequestResponse> = rows.toList()
        override suspend fun createRequest(userId: Long, serviceId: Long, comment: String, address: String): RequestResponse {
            creates++
            if (failBefore) throw IOException("fixture pre-commit failure")
            val r = RequestResponse(creates.toLong(), userId, serviceId, "Создана", comment, address, Instant.fromEpochMilliseconds(1700000000000))
            rows.add(r)
            if (timeoutAfter) throw IOException("fixture timeout after commit")
            return r
        }
    }
    private suspend fun reset(): Api {
        SL.db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        SL.sessions = SessionStore(ctx)
        AppState.reset()
        AppState.login("fixture", 1, "fixture")
        return Api().also { SL.api = it }
    }
    private fun ready(text: String, timeout: Long = 15000): Boolean = device.wait(Until.hasObject(By.textContains(text)), timeout)
    private fun click(text: String) {
        check(ready(text)) { "Missing UI selector: $text" }
        device.findObject(By.textContains(text)).click()
    }
    private fun memory(): JSONObject {
        val m = Debug.MemoryInfo()
        Debug.getMemoryInfo(m)
        return JSONObject().put("totalPssKb", m.totalPss).put("dalvikPssKb", m.dalvikPss)
            .put("nativePssKb", m.nativePss).put("javaUsedBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
    }
    @Test fun collectBaseline() = runBlocking {
        result.put("package", ctx.packageName).put("androidApi", android.os.Build.VERSION.SDK_INT)
        try {
            var api = reset()
            api.timeoutAfter = true
            val timeoutError = SL.submitRequest(1, "valid baseline comment", "fixture address")
            SL.refreshRequests()
            val duplicates = SL.loadLocalRequests()
            result.put("timeoutAfterCommit", JSONObject().put("serverRows", api.rows.size).put("localRows", duplicates.size)
                .put("excessDuplicates", duplicates.size - api.rows.size).put("submitResult", timeoutError)
                .put("localIds", JSONArray(duplicates.map { it.id })).put("localStatuses", JSONArray(duplicates.map { it.status })))
            SL.db.close()

            api = reset()
            api.failBefore = true
            SL.submitRequest(1, "valid baseline comment", "fixture address")
            api.failBefore = false
            repeat(5) { SL.refreshRequests() }
            result.put("preCommitFailure", JSONObject().put("refreshesAfterRecovery", 5).put("createCalls", api.creates)
                .put("serverRows", api.rows.size).put("localPendingRows", SL.loadLocalRequests().size)
                .put("undeliveredIntents", 1 - api.rows.size)
                .put("localStatuses", JSONArray(SL.loadLocalRequests().map { it.status })))
            SL.db.close()

            reset()
            repeat(10) { SL.db.requestDao().insert(row(it + 1L, if (it < 5) 1 else 2)) }
            val leaked = SL.loadLocalRequests().count { it.userId != AppState.userId }
            result.put("multiuser", JSONObject().put("totalRows", 10).put("foreignRowsVisible", leaked))
            SL.db.close()

            api = reset()
            var duplicateRuns = 0
            var totalNotifications = 0
            repeat(50) {
                SL.db.requestDao().clear()
                AppState.notifications.clear()
                SL.db.requestDao().insert(row(1))
                api.rows.clear()
                api.rows.add(row(1, status = "В работе"))
                coroutineScope {
                    val start = CompletableDeferred<Unit>()
                    val a = async(Dispatchers.IO) { start.await(); SL.refreshRequests() }
                    val b = async(Dispatchers.IO) { start.await(); SL.refreshRequests() }
                    start.complete(Unit)
                    awaitAll(a, b)
                }
                totalNotifications += AppState.notifications.size
                if (AppState.notifications.size > 1) duplicateRuns++
            }
            result.put("concurrentRefresh", JSONObject().put("trials", 50).put("duplicateTrials", duplicateRuns)
                .put("notifications", totalNotifications).put("scheduling", "two IO coroutines released by shared start gate; no production hooks"))
            SL.db.close()
            uiMeasurements()
        } finally {
            File(ctx.filesDir, "baseline-metrics.json").writeText(result.toString(2))
            Log.i("BASELINE_METRICS", result.toString())
        }
    }
    private suspend fun uiMeasurements() {
        // MainActivity initializes the unmodified production locator itself.
        SL.sessions.clear()
        AppState.reset()
        val launch = SystemClock.elapsedRealtimeNanos()
        ctx.startActivity(Intent().setClassName(ctx.packageName, "ru.course.cityservices.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        check(ready("Заполнить демо-доступ"))
        result.put("launchLoginReadyMs", (SystemClock.elapsedRealtimeNanos() - launch) / 1e6)
        click("Заполнить демо-доступ")
        val login = SystemClock.elapsedRealtimeNanos()
        click("Войти")
        check(ready("Мои заявки"))
        check(ready("Уборка"))
        result.put("loginCatalogReadyMs", (SystemClock.elapsedRealtimeNanos() - login) / 1e6)
        SL.api = Api()
        val history = JSONArray()
        for (count in listOf(100, 1000)) {
            SL.db.requestDao().clear()
            repeat(count) { SL.db.requestDao().insert(row(it + 1L)) }
            val reads = JSONArray()
            repeat(10) {
                val start = SystemClock.elapsedRealtimeNanos()
                check(SL.loadLocalRequests().size == count)
                reads.put((SystemClock.elapsedRealtimeNanos() - start) / 1e6)
            }
            val start = SystemClock.elapsedRealtimeNanos()
            click("Мои заявки")
            check(ready("baseline-record-$count"))
            history.put(JSONObject().put("records", count).put("daoReadMs", reads)
                .put("uiFirstRecordReadyMs", (SystemClock.elapsedRealtimeNanos() - start) / 1e6))
            click("Назад")
        }
        result.put("history", history)
        device.executeShellCommand("dumpsys gfxinfo ${ctx.packageName} reset")
        click("Мои заявки")
        check(ready("baseline-record-1000"))
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(20) { device.swipe(w / 2, h * 4 / 5, w / 2, h / 3, 20) }
        device.waitForIdle()
        File(ctx.filesDir, "gfxinfo-scroll.txt").writeText(device.executeShellCommand("dumpsys gfxinfo ${ctx.packageName} framestats"))
        result.put("scroll", JSONObject().put("swipes", 20).put("records", 1000))
        click("Назад")
        val before = memory()
        val samples = JSONArray()
        repeat(20) {
            click("Мои заявки")
            check(ready("baseline-record-1000"))
            click("Назад")
            check(ready("Мои заявки"))
            if ((it + 1) % 5 == 0) samples.put(JSONObject().put("cycles", it + 1).put("memory", memory()))
        }
        result.put("navigationMemory", JSONObject().put("before", before).put("samples", samples)
            .put("after", memory()).put("forcedGc", false))
    }
}
