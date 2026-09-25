package ru.course.cityservices.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.course.cityservices.core.ServiceLocator
import ru.course.cityservices.model.ServiceResponse

@Composable
fun ServiceDetailScreen(
    service: ServiceResponse,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var comment by remember { mutableStateOf(value = "") }
    var address by remember { mutableStateOf(value = "") }
    var error by remember { mutableStateOf<String?>(value = null) }
    var sending by remember { mutableStateOf(value = false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 20.dp)
    ) {
        Text(
            text = service.title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = service.category,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        Text(text = service.description)
        Spacer(modifier = Modifier.height(height = 20.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = comment,
            onValueChange = { value -> comment = value },
            label = {
                Text(text = "Комментарий")
            },
            minLines = 3
        )
        Spacer(modifier = Modifier.height(height = 12.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = address,
            onValueChange = { value -> address = value },
            label = {
                Text(text = "Адрес (улица, дом)")
            },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(height = 12.dp))

        error?.let { text ->
            Text(
                text = text,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(height = 12.dp))
        }

        Row {
            OutlinedButton(
                onClick = onBack,
                enabled = !sending
            ) {
                Text(text = "Назад")
            }
            Spacer(modifier = Modifier.width(width = 8.dp))
            Button(
                onClick = {
                    if (comment.isBlank()) {
                        error = "Введите комментарий"
                        return@Button
                    }
                    sending = true
                    error = null

                    scope.launch {
                        val result = ServiceLocator.submitRequest(
                            serviceId = service.id,
                            comment = comment,
                            address = address,
                        )
                        sending = false
                        if (result == null) onCreated() else error = result
                    }
                },
                enabled = !sending
            ) {
                Text(text = if (sending) "Отправка…" else "Создать заявку")
            }
        }
    }
}
