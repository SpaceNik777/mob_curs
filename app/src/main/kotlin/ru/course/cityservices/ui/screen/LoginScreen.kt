package ru.course.cityservices.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.course.cityservices.core.ServiceLocator

class LoginViewModel : ViewModel() {

    fun login(username: String, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(ServiceLocator.login(username = username, password = password))
        }
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val viewModel: LoginViewModel = viewModel()

    var username by remember { mutableStateOf(value = "") }
    var password by remember { mutableStateOf(value = "") }
    var error by remember { mutableStateOf<String?>(value = null) }
    var loading by remember { mutableStateOf(value = false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Городские услуги",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Личный кабинет жителя",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(height = 24.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = username,
            onValueChange = { text -> username = text },
            label = {
                Text(text = "Имя пользователя")
            },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = password,
            onValueChange = { text -> password = text },
            label = {
                Text(text = "Пароль")
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(height = 16.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    error = "Заполните имя пользователя и пароль"
                } else {
                    loading = true
                    error = null

                    viewModel.login(username = username, password = password) { result ->
                        loading = false

                        if (result == null) onLoggedIn() else error = result
                    }
                }
            },
            enabled = !loading,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size = 18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(width = 8.dp))
            }
            Text(text = if (loading) "Выполняется вход…" else "Войти")
        }

        error?.let { text ->
            Spacer(modifier = Modifier.height(height = 8.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(height = 24.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                username = "RUT"
                password = "RUT"
            },
        ) {
            Text(text = "Заполнить демо-доступ")
        }
        Spacer(modifier = Modifier.height(height = 8.dp))
        Text(
            text = "Демо-доступ: RUT / RUT",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
