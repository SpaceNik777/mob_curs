package ru.course.cityservices.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.course.cityservices.core.ServiceLocator
import ru.course.cityservices.model.ServiceResponse
import ru.course.cityservices.state.AppState
import ru.course.cityservices.ui.component.ServiceCard

class ServicesViewModel : ViewModel() {

    var services by mutableStateOf<List<ServiceResponse>>(value = emptyList())
        private set

    fun load() {
        viewModelScope.launch {
            ServiceLocator.loadServices()?.let { response -> services = response }
        }
    }
}

@Composable
fun ServicesScreen(
    onOpen: (ServiceResponse) -> Unit,
    onRequests: () -> Unit,
    onNotifications: () -> Unit,
    onLogout: () -> Unit
) {
    var loading by remember { mutableStateOf(value = true) }

    LaunchedEffect(Unit) {
        loading = true
        ServiceLocator.loadServices()
        loading = false
    }

    // Изменение любой из этих строк (имя, каталог, ошибки, уведомления) перерисовывает экран целиком
    val services = AppState.services
    val unread = AppState.notifications.count { item -> !item.isRead }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(weight = 1f)) {
                Text(
                    text = "Здравствуйте, ${AppState.username}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Каталог городских услуг",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onLogout) {
                Text(text = "Выйти")
            }
        }

        Spacer(modifier = Modifier.height(height = 12.dp))
        Row {
            Button(
                modifier = Modifier.weight(weight = 1f),
                onClick = onRequests,
            ) {
                Text(text = "Мои заявки")
            }
            Spacer(modifier = Modifier.width(width = 8.dp))
            Button(
                modifier = Modifier.weight(weight = 1f),
                onClick = onNotifications,
            ) {
                Text(text = if (unread > 0) "Уведомления ($unread)" else "Уведомления")
            }
        }

        AppState.lastError?.let { message ->
            Spacer(modifier = Modifier.height(height = 8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(height = 12.dp))

        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            services.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Каталог пуст. Проверьте соединение.")
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.weight(weight = 1f)) {
                    // Compose не понимает, какие элементы те же самые. Нужны ключи
                    items(items = services) { service ->
                        ServiceCard(
                            modifier = Modifier.padding(vertical = 6.dp),
                            service = service,
                            onOpen = {
                                // Аналитика вызывается прямо из клика в UI
                                ServiceLocator.analytics.track(
                                    event = "service_opened",
                                    params = mapOf("id" to service.id.toString())
                                )

                                onOpen(service)
                            },
                        )
                    }
                }
            }
        }
    }
}
