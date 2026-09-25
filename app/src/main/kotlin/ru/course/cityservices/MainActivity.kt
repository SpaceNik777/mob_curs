package ru.course.cityservices

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.course.cityservices.core.ServiceLocator
import ru.course.cityservices.ui.theme.CityServicesTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ServiceLocator.init(context = this)

        setContent {
            CityServicesTheme {
                CityServicesApp()
            }
        }
    }
}
