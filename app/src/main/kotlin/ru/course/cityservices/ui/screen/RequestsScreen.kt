package ru.course.cityservices.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.course.cityservices.core.ServiceLocator
import ru.course.cityservices.domain.RequestStatus
import ru.course.cityservices.model.RequestResponse
import ru.course.cityservices.ui.component.StatusBadge
import ru.course.cityservices.util.TimeFormat

// Эта ViewModel, в отличие от остальных, используется по назначению.
// Но и она идёт напрямую к бог-объекту, а не к репозиториям
class RequestsViewModel : ViewModel() {

    var requests by mutableStateOf<List<RequestResponse>>(value = emptyList())
        private set

    var loading by mutableStateOf(value = false)
        private set

    fun load() {
        viewModelScope.launch {
            loading = true

            // Локальная база: читаются заявки всех пользователей устройства
            // (фильтр по пользователю в DAO не подключен)
            requests = ServiceLocator.loadLocalRequests()

            // Сервер: обновит статусы и пополнит глобальные уведомления
            ServiceLocator.refreshRequests()

            requests = ServiceLocator.loadLocalRequests()
            loading = false
        }
    }
}

@Composable
fun RequestsScreen(onBack: () -> Unit) {
    val viewModel: RequestsViewModel = viewModel()
    var filter by remember { mutableStateOf(value = "Все") }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(weight = 1f),
                text = "Мои заявки",
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = onBack) {
                Text(text = "Назад")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // List-параметр делает компонент "нестабильным" - чипы перерисовываются при каждой рекомпозиции экрана
        StatusFilterChips(
            options = listOf("Все") + RequestStatus.entries.map { entry -> entry.title },
            selected = filter,
            onSelect = { newFilter -> filter = newFilter }
        )
        Spacer(modifier = Modifier.height(height = 8.dp))

        // Фильтрация и сортировка выполняются в теле композиции при каждой рекомпозиции, а не в ViewModel или remember/derivedStateOf
        val visible = viewModel.requests
            .filter { request -> filter == "Все" || request.status == filter }
            .sortedByDescending { response -> response.createdAt }

        when {
            viewModel.loading && visible.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            visible.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Заявок пока нет")
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.weight(weight = 1f)) {
                    // Нет ключа
                    items(items = visible) { request ->
                        RequestCard(request = request)
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: RequestResponse,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(all = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(weight = 1f),
                    text = "Заявка №${request.id}",
                    style = MaterialTheme.typography.titleSmall,
                )
                StatusBadge(statusTitle = request.status)
            }
            Spacer(modifier = Modifier.height(height = 4.dp))
            Text(
                text = request.comment,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Адрес: ${request.address}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = TimeFormat.relative(instant = request.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusFilterChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(state = rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(text = option)
                }
            )
        }
    }
}
