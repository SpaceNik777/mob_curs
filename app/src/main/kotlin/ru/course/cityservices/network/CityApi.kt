package ru.course.cityservices.network

import ru.course.cityservices.model.RequestResponse
import ru.course.cityservices.model.ServiceResponse

// Единственная «граница» сети в проекте.
// Преднамеренно: интерфейс скрывает только транспорт, но не модели -
// наружу отдаются серверные DTO, которые затем расползаются по всему приложению.
interface CityApi {

    suspend fun login(username: String, password: String): AuthResponse

    suspend fun getServices(): List<ServiceResponse>

    suspend fun getRequests(userId: Long): List<RequestResponse>

    suspend fun createRequest(
        userId: Long,
        serviceId: Long,
        comment: String,
        address: String
    ): RequestResponse
}

data class AuthResponse(
    val success: Boolean,
    val userId: Long,
    val displayName: String,
    val error: String? = null
)
