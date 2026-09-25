package ru.course.cityservices.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

data class ServiceResponse(
    val id: Long,
    val title: String,
    val description: String,
    val category: String
)

@Entity(tableName = "requests")
data class RequestResponse(
    @PrimaryKey val id: Long,
    val userId: Long,
    val serviceId: Long,
    val status: String,
    val comment: String,
    val address: String,
    val createdAt: Instant,
)

data class NotificationItem(
    val id: Long,
    val requestId: Long,
    val text: String,
    val createdAt: Instant,
    var isRead: Boolean = false,
)
