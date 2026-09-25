package ru.course.cityservices.domain

object RequestValidator {

    const val MIN_COMMENT_LENGTH = 10

    fun validateComment(comment: String): String? = when {
        comment.isBlank() -> "Комментарий не заполнен"
        comment.length < MIN_COMMENT_LENGTH ->
            "Комментарий слишком короткий (минимум $MIN_COMMENT_LENGTH символов)"

        else -> null
    }

    fun validateAddress(address: String): String? = when {
        address.isBlank() -> "Адрес не заполнен"

        else -> null
    }

    /**
     * Полная проверка: возвращает список всех ошибок сразу
     */
    fun validate(comment: String, address: String): List<String> =
        listOfNotNull(validateComment(comment = comment), validateAddress(address = address))
}
