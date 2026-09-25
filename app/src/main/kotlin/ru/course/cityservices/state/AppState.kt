package ru.course.cityservices.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.course.cityservices.model.NotificationItem
import ru.course.cityservices.model.ServiceResponse

// Глобальное изменяемое состояние.
// Пишут в него ServiceLocator, ViewModel-ы и сами экраны - кто владеет каждым
// полем и кто имеет право его менять, из кода неочевидно
object AppState {

    var isLoggedIn by mutableStateOf(value = false)
    var username by mutableStateOf(value = "")
    var userId by mutableLongStateOf(value = -1L)

    // Так правильнее. Используем Kotlin Coroutines, вместо Compose напрямую
    private var _newUserId: MutableStateFlow<Int> = MutableStateFlow(-1)
    var newUserId: StateFlow<Int> = _newUserId.asStateFlow()

    var services by mutableStateOf<List<ServiceResponse>>(value = emptyList())
    var selectedService by mutableStateOf<ServiceResponse?>(value = null)
    var lastError by mutableStateOf<String?>(value = null)

    val notifications = mutableStateListOf<NotificationItem>()

    fun login(username: String, userId: Long, displayName: String) {
        this.username = displayName
        this.userId = userId
        this.isLoggedIn = true
        this.services = emptyList()
        this.selectedService = null
        this.lastError = null
    }

    fun reset() {
        isLoggedIn = false
        username = ""
        userId = -1L
        services = emptyList()
        selectedService = null
        lastError = null
        notifications.clear()
    }
}
