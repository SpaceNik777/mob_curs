package ru.course.cityservices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.course.cityservices.core.ServiceLocator
import ru.course.cityservices.state.AppState
import ru.course.cityservices.ui.screen.LoginScreen
import ru.course.cityservices.ui.screen.NotificationsScreen
import ru.course.cityservices.ui.screen.RequestsScreen
import ru.course.cityservices.ui.screen.ServiceDetailScreen
import ru.course.cityservices.ui.screen.ServicesScreen
import kotlin.time.Duration.Companion.seconds

@Composable
fun CityServicesApp() {
    var screen by remember { mutableStateOf("splash") }
    val scope = rememberCoroutineScope()

    // Восстановление сохраненной сессии
    LaunchedEffect(Unit) {
        val restored = ServiceLocator.restoreSession()

        screen = if (restored) "services" else "login"
    }

    // Опрос сервера живет прямо в корневой композиции.
    // Каждое обновление пишет в глобальное состояние - и экраны, читающие его, перерисовываются целиком
    LaunchedEffect(Unit) {
        while (true) {
            delay(duration = 30.seconds)

            if (AppState.isLoggedIn) {
                ServiceLocator.refreshRequests()
            }
        }
    }

    Scaffold { innerPadding ->
        Box(modifier = Modifier.padding(paddingValues = innerPadding)) {
            when (screen) {
                "splash" -> SplashScreen()

                "login" -> LoginScreen(onLoggedIn = { screen = "services" })

                "services" -> ServicesScreen(
                    onOpen = { service ->
                        // Параметры перехода кладутся в глобальное состояние
                        AppState.selectedService = service
                        screen = "service"
                    },
                    onRequests = { screen = "requests" },
                    onNotifications = { screen = "notifications" },
                    onLogout = {
                        scope.launch { ServiceLocator.logout() }
                        screen = "login"
                    }
                )

                "service" -> {
                    val service = AppState.selectedService

                    if (service != null) {
                        ServiceDetailScreen(
                            service = service,
                            onBack = { screen = "services" },
                            onCreated = { screen = "requests" }
                        )
                    } else {
                        // Запись состояния во время композиции
                        screen = "services"
                    }
                }

                "requests" -> RequestsScreen(onBack = { screen = "services" })

                "notifications" -> NotificationsScreen(onBack = { screen = "services" })
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
