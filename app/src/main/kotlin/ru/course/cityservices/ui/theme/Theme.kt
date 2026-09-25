package ru.course.cityservices.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CityBlue = Color(0xFF2E5FA3)
private val CitySand = Color(0xFFB4762A)
private val CityBackground = Color(0xFFF6F7F9)

private val LightColors = lightColorScheme(
    primary = CityBlue,
    secondary = CitySand,
    background = CityBackground,
)

@Composable
fun CityServicesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
