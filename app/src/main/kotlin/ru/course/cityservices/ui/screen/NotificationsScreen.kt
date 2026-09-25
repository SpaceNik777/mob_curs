package ru.course.cityservices.ui.screen

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.course.cityservices.state.AppState
import ru.course.cityservices.util.TimeFormat

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(weight = 1f),
                text = "Уведомления",
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = onBack) {
                Text(text = "Назад")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // mutableStateListOf замечает добавление/удаление, но не замечает
        // изменение поля isRead у существующего элемента - поэтому счетчик
        // непрочитанных на экране каталога после клика не обновится
        val notifications = AppState.notifications

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(weight = 1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Уведомлений пока нет")
            }
        } else {
            LazyColumn(modifier = Modifier.weight(weight = 1f)) {
                // Без key
                items(items = notifications) { notification ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        onClick = { notification.isRead = true }
                    ) {
                        Column(modifier = Modifier.padding(all = 16.dp)) {
                            Text(
                                text = notification.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold
                            )
                            Text(
                                text = TimeFormat.relative(instant = notification.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
