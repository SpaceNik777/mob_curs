package ru.course.cityservices.core

import android.content.Context
import androidx.room.Room
import ru.course.cityservices.analytic.AnalyticsTracker
import ru.course.cityservices.domain.RequestValidator
import ru.course.cityservices.model.NotificationItem
import ru.course.cityservices.model.RequestResponse
import ru.course.cityservices.model.ServiceResponse
import ru.course.cityservices.network.CityApi
import ru.course.cityservices.network.FakeApi
import ru.course.cityservices.state.AppState
import ru.course.cityservices.storage.AppDatabase
import ru.course.cityservices.storage.SessionStore
import kotlin.time.Clock

// «Бог-объект». Один компонент держит сеть, базу, хранилище сессии и аналитику, выполняет
// бизнес-операции и при этом ещё и пишет в глобальное состояние.
object ServiceLocator {

    lateinit var api: CityApi
    lateinit var db: AppDatabase
    lateinit var sessions: SessionStore

    val analytics = AnalyticsTracker()

    private var notificationId = 1L

    fun init(context: Context) {
        api = FakeApi()
        db = Room.databaseBuilder(
            context = context.applicationContext,
            klass = AppDatabase::class.java,
            name = "city-services.db",
        ).build()
        sessions = SessionStore(context = context.applicationContext)
    }

    suspend fun restoreSession(): Boolean {
        val saved = sessions.load() ?: return false

        AppState.login(
            userId = saved.userId,
            username = saved.username,
            displayName = saved.displayName,
        )

        return true
    }

    suspend fun login(username: String, password: String): String? =
        try {
            val response = api.login(username = username, password = password)

            if (response.success) {
                AppState.login(
                    username = username,
                    userId = response.userId,
                    displayName = response.displayName,
                )

                sessions.save(
                    username = username,
                    userId = response.userId,
                    displayName = response.displayName,
                )
                analytics.track(event = "login_success", params = mapOf("user" to username))

                null
            } else {
                analytics.track(
                    event = "login_failed",
                    params = mapOf("reason" to (response.error ?: "unknown"))
                )

                response.error
            }
        } catch (exception: Exception) {
            analytics.track(
                event = "login_error",
                params = mapOf("type" to exception.javaClass.simpleName)
            )

            "Не удалось выполнить вход. Проверьте соединение."
        }

    suspend fun logout() {
        sessions.clear()
        AppState.reset()

        analytics.track(event = "logout")
    }

    suspend fun loadServices(): List<ServiceResponse>? =
        try {
            val loaded = api.getServices()

            AppState.services = loaded
            AppState.lastError = null

            analytics.track(
                event = "catalog_loaded",
                params = mapOf("count" to loaded.size.toString())
            )

            loaded
        } catch (exception: Exception) {
            AppState.lastError = "Не удалось загрузить каталог услуг"

            null
        }

    suspend fun submitRequest(serviceId: Long, comment: String, address: String): String? {
        // Валидатор подключен частично - комментарий проверяется, адрес нет (RequestValidator.validateAddress не вызывается)
        val commentError = RequestValidator.validateComment(comment = comment)
        if (commentError != null) {
            return commentError
        }

        return try {
            val created = api.createRequest(
                comment = comment,
                address = address,
                serviceId = serviceId,
                userId = AppState.userId,
            )

            db.requestDao().insert(request = created)
            analytics.track(
                event = "request_created",
                params = mapOf("serviceId" to serviceId.toString())
            )

            null
        } catch (exception: Exception) {
            // Сеть упала. Данные пользователя сохраняем локально.
            // Идентификатор выдаем сами (текущее время), поэтому при синхронизации та же заявка с сервера (с серверным id) станет дублем.
            // Повторной отправки нет - локальная заявка навсегда остаётся «Создана»
            val local = RequestResponse(
                id = System.currentTimeMillis(),
                userId = AppState.userId,
                serviceId = serviceId,
                status = "Создана",
                comment = comment,
                address = address,
                createdAt = Clock.System.now()
            )

            db.requestDao().insert(request = local)
            analytics.track(event = "request_saved_locally")

            "Не удалось отправить заявку. Заявка сохранена локально."
        }
    }

    suspend fun refreshRequests(): String? {
        if (!AppState.isLoggedIn) return null

        return try {
            val fromServer = api.getRequests(userId = AppState.userId)
            val local = db.requestDao().all()
            val localById = local.associateBy { request -> request.id }

            fromServer.forEach { server ->
                val old = localById[server.id]

                when {
                    old == null -> {
                        // Незнакомая серверная заявка: тихо вставляем, без уведомления
                        db.requestDao().insert(request = server)
                    }

                    old.status != server.status -> {
                        db.requestDao().insert(request = server)
                        AppState.notifications.add(
                            index = 0,
                            element = NotificationItem(
                                id = notificationId++,
                                requestId = server.id,
                                text = "Статус заявки №${server.id}: ${old.status} → ${server.status}",
                                createdAt = Clock.System.now()
                            )
                        )
                        analytics.track(
                            event = "request_status_changed",
                            params = mapOf("requestId" to server.id.toString())
                        )
                    }
                }
            }

            AppState.lastError = null

            null
        } catch (exception: Exception) {
            AppState.lastError = "Не удалось обновить заявки"

            "Не удалось обновить заявки"
        }
    }

    suspend fun loadLocalRequests(): List<RequestResponse> {
        // Используется all() - заявки всех пользователей устройства. Фильтр forUser(userId) в DAO существует, но не подключен
        return db.requestDao().all()
    }
}
