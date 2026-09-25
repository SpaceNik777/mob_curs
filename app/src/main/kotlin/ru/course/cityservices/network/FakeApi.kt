package ru.course.cityservices.network

import android.util.Log
import kotlinx.coroutines.delay
import ru.course.cityservices.model.RequestResponse
import ru.course.cityservices.model.ServiceResponse
import java.io.IOException
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

// Фейковый «сервер». Симулирует задержки, периодические сбои и «жизнь» заявок
// на стороне сервера (статусы меняются сами)
//
// Сбои детерминированы (общий счетчик вызовов):
//   - каждый 5-й вызов createRequest: «сервер принял заявку, но ответ не дошёл»
//     (заявка регистрируется на сервере, клиент получает IOException);
//   - каждый 7-й вызов getServices падает;
//   - каждый 11-й вызов login возвращает ошибку сервера;
//   - каждый 13-й вызов getRequests падает.
class FakeApi : CityApi {

    private var calls = 0

    private data class DemoUser(val id: Long, val displayName: String, val password: String)

    private val users = mapOf(
        "RUT" to DemoUser(id = 1L, displayName = "RUT MIIT", password = "RUT"),
    )

    private val services = listOf(
        ServiceResponse(
            id = 1L,
            title = "Уборка снега",
            description = "Сообщить о необходимости расчистки снега.",
            category = "Дороги"
        ),
        ServiceResponse(
            id = 2L,
            title = "Освещение",
            description = "Сообщить о неисправном уличном фонаре.",
            category = "Дворы"
        ),
        ServiceResponse(
            id = 3L,
            title = "Дорожное покрытие",
            description = "Сообщить о ямах и повреждениях дороги.",
            category = "Дороги"
        ),
        ServiceResponse(
            id = 4L,
            title = "Вывоз мусора",
            description = "Сообщить о переполненном контейнере.",
            category = "Дворы"
        ),
        ServiceResponse(
            id = 5L,
            title = "Водоснабжение",
            description = "Сообщить об утечке или отключении воды.",
            category = "Коммуникации"
        ),
        ServiceResponse(
            id = 6L,
            title = "Благоустройство",
            description = "Предложить улучшение двора или парка.",
            category = "Дворы"
        ),
        ServiceResponse(
            id = 7L,
            title = "Парковки",
            description = "Сообщить о нарушении правил парковки или разметки.",
            category = "Транспорт"
        ),
        ServiceResponse(
            id = 8L,
            title = "Зелёные насаждения",
            description = "Сообщить о повреждённом дереве или клумбе.",
            category = "Дворы"
        )
    )

    // Состояние сервера. Идентификаторы выдаёт сервер (100001, 100002, ...),
    // поэтому они отличаются от локальных (System.currentTimeMillis())
    private val serverRequests = mutableListOf<RequestResponse>()
    private var nextServerId = 100_001L

    init {
        seedDemoRequests()
    }

    private fun seedDemoRequests() {
        val now = Clock.System.now()

        serverRequests += RequestResponse(
            id = nextServerId++,
            userId = 1L,
            serviceId = 1L,
            status = "В работе",
            comment = "Пункт у дома 12 не расчищен третий день.",
            address = "ул. Ленина, 12",
            createdAt = now - 3.days
        )
        serverRequests += RequestResponse(
            id = nextServerId++,
            userId = 1L,
            serviceId = 2L,
            status = "Выполнена",
            comment = "Фонарь у подъезда не горит с пятницы.",
            address = "ул. Ленина, 12",
            createdAt = now - 6.days
        )
        serverRequests += RequestResponse(
            id = nextServerId++,
            userId = 1L,
            serviceId = 4L,
            status = "Принята",
            comment = "Контейнер во дворе переполнен.",
            address = "пр. Мира, 4",
            createdAt = now - 1.days
        )
        serverRequests += RequestResponse(
            id = nextServerId++,
            userId = 2L,
            serviceId = 3L,
            status = "Создана",
            comment = "Яма на выезде со двора, примерно 40 см.",
            address = "ул. Садовая, 3",
            createdAt = now - 5.days
        )
    }

    override suspend fun login(username: String, password: String): AuthResponse {
        delay(duration = LOGIN_DELAY.milliseconds)
        calls++

        if (calls % 11 == 0) {
            return AuthResponse(
                success = false,
                userId = -1L,
                displayName = "",
                error = "Сервер авторизации недоступен. Попробуйте позже."
            )
        }

        val user = users[username.trim()]
            ?: return AuthResponse(
                success = false,
                userId = -1L,
                displayName = "",
                error = "Неверное имя пользователя или пароль."
            )

        if (user.password != password) {
            return AuthResponse(
                success = false,
                userId = -1L,
                displayName = "",
                error = "Неверное имя пользователя или пароль."
            )
        }

        return AuthResponse(success = true, userId = user.id, displayName = user.displayName)
    }

    override suspend fun getServices(): List<ServiceResponse> {
        delay(duration = CATALOG_DELAY.milliseconds)
        calls++

        if (calls % 7 == 0) {
            throw IOException("Сеть недоступна")
        }

        return services
    }

    override suspend fun getRequests(userId: Long): List<RequestResponse> {
        delay(REQUESTS_DELAY.milliseconds)
        calls++

        if (calls % 13 == 0) {
            throw IOException("Сеть недоступна")
        }

        advanceStatuses()
        return serverRequests.filter { request -> request.userId == userId }
    }

    override suspend fun createRequest(
        userId: Long,
        serviceId: Long,
        comment: String,
        address: String
    ): RequestResponse {
        delay(duration = CREATE_DELAY.milliseconds)
        calls++

        if (calls % 5 == 0) {
            // Имитация сервер принял заявку, но ответ не дошёл до клиента.
            // Заявка регистрируется на сервере, клиент видит ошибку и сохраняет
            // локальную копию с другим id - источник дублей при синхронизации
            serverRequests += RequestResponse(
                id = nextServerId++,
                userId = userId,
                serviceId = serviceId,
                status = "Создана",
                comment = comment,
                address = address,
                createdAt = Clock.System.now(),
            )

            throw IOException("Тайм-аут запроса")
        }

        val created = RequestResponse(
            id = nextServerId++,
            userId = userId,
            serviceId = serviceId,
            status = "Создана",
            comment = comment,
            address = address,
            createdAt = Clock.System.now(),
        )
        serverRequests += created

        return created
    }

    // Эмитация жизни на сервере - часть заявок переходит в следующий статус
    private fun advanceStatuses() {
        val order = listOf("Создана", "Принята", "В работе", "Выполнена")

        for (i in serverRequests.indices) {
            val current = serverRequests[i]
            val index = order.indexOf(current.status)
            if (index in 0 until order.lastIndex && Random.nextFloat() < 0.35f) {
                serverRequests[i] = current.copy(status = order[index + 1])
            }
        }
    }

    companion object {

        const val LOGIN_DELAY = 700L

        const val CATALOG_DELAY = 800L

        const val REQUESTS_DELAY = 600L

        const val CREATE_DELAY = 900L
    }
}
