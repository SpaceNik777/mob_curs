package ru.course.cityservices.domain

enum class RequestStatus(val title: String) {
    CREATED(title = "Создана"),

    ACCEPTED(title = "Принята"),

    IN_PROGRESS(title = "В работе"),

    DONE(title = "Выполнена"),
    ;

    /**
     * Единственный допустимый следующий статус; для DONE - null
     */
    fun next(): RequestStatus? = when (this) {
        CREATED -> ACCEPTED
        ACCEPTED -> IN_PROGRESS
        IN_PROGRESS -> DONE
        DONE -> null
    }

    companion object {

        /**
         * Строки статусов приходят с сервера как есть; неизвестная строка - null
         */
        fun fromTitle(title: String): RequestStatus? =
            entries.firstOrNull { entry -> entry.title == title }
    }
}

/**
 * Доменная модель заявки
 */
data class ServiceRequest(
    val id: Long,
    val serviceId: Long,
    val comment: String,
    val address: String,
    val createdAt: Long,
    val status: RequestStatus,
)

/**
 * Правила жизни заявки: какие переходы статусов разрешены
 */
object StatusRules {

    fun allowedTransitions(from: RequestStatus): List<RequestStatus> =
        listOfNotNull(element = from.next())

    fun isTerminal(status: RequestStatus): Boolean = status == RequestStatus.DONE
}
