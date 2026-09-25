package ru.course.cityservices.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Пример того, как надо :)
class RequestValidatorTest {

    @Test
    fun `пустой комментарий отклоняется`() {
        assertEquals("Комментарий не заполнен", RequestValidator.validateComment("   "))
    }

    @Test
    fun `короткий комментарий отклоняется`() {
        assertEquals(
            "Комментарий слишком короткий (минимум 10 символов)",
            RequestValidator.validateComment("яма")
        )
    }

    @Test
    fun `достаточный комментарий принимается`() {
        assertNull(RequestValidator.validateComment("Яма перед подъездом, примерно 30 см."))
    }

    @Test
    fun `пустой адрес отклоняется`() {
        assertEquals("Адрес не заполнен", RequestValidator.validateAddress(" "))
    }

    @Test
    fun `полная проверка возвращает обе ошибки сразу`() {
        val errors = RequestValidator.validate("", "")
        assertEquals(listOf("Комментарий не заполнен", "Адрес не заполнен"), errors)
    }

    @Test
    fun `переход статусов идёт по цепочке`() {
        assertEquals(RequestStatus.ACCEPTED, RequestStatus.CREATED.next())
        assertEquals(RequestStatus.IN_PROGRESS, RequestStatus.ACCEPTED.next())
        assertEquals(RequestStatus.DONE, RequestStatus.IN_PROGRESS.next())
        assertNull(RequestStatus.DONE.next())
    }

    @Test
    fun `незнакомая строка статуса не маппится`() {
        assertNull(RequestStatus.fromTitle("Закрыта"))
        assertEquals(RequestStatus.CREATED, RequestStatus.fromTitle("Создана"))
    }
}
