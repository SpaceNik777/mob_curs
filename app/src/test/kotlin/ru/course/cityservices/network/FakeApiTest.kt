package ru.course.cityservices.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

// 1. Тесты делят один экземпляр FakeApi и зависят от общего счётчика вызовов:
//    «пятая заявка падает» проходит только если перед ним выполнено ровно
//    четыре createRequest. Запуск этого теста одного - падение.
// 2. Порядок выполнения тестов в JUnit не гарантирован: любой другой тест,
//    тронувший счетчик, ломает ожидания.
// 3. runBlocking с реальными задержками: набор выполняется несколько секунд.
class FakeApiTest {

    @Test
    fun `четыре заявки подряд создаются успешно`() = runBlocking {
        repeat(times = 4) { i ->
            val created = api.createRequest(
                userId = 1L,
                serviceId = 1L,
                comment = "Заявка номер ${i + 1}",
                address = "ул. Ленина, 12",
            )
            assertEquals("Создана", created.status)
        }
    }

    @Test
    fun `пятая заявка падает с тайм-аутом`() = runBlocking {
        // Предположение: перед этим тестом уже выполнено ровно четыре создания
        try {
            api.createRequest(
                userId = 1L,
                serviceId = 1L,
                comment = "Пятая заявка",
                address = "ул. Ленина, 12",
            )
            throw AssertionError("Ожидался IOException, но заявка создалась")
        } catch (expected: IOException) {
            // ожидаемо
        }
    }

    @Test
    fun `каталог содержит восемь услуг`() = runBlocking {
        assertEquals(8, api.getServices().size)
    }

    companion object {

        private val api = FakeApi()
    }
}
