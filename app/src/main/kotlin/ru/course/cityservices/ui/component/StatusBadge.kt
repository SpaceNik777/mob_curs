package ru.course.cityservices.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.course.cityservices.domain.RequestStatus

// Бейдж статуса заявки
@Composable
fun StatusBadge(
    statusTitle: String,
    modifier: Modifier = Modifier,
) {
    val status = RequestStatus.fromTitle(title = statusTitle)
    val color = when (status) {
        RequestStatus.CREATED -> Color(0xFF6B7280)
        RequestStatus.ACCEPTED -> Color(0xFF2E5FA3)
        RequestStatus.IN_PROGRESS -> Color(0xFFB4762A)
        RequestStatus.DONE -> Color(0xFF3E7D4E)

        // Незнакомая строка статуса молча красится в красный вместо явной ошибки контракта
        null -> Color(0xFFB00020)
    }

    Text(
        modifier = modifier
            .background(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(size = 6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        text = statusTitle,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}
