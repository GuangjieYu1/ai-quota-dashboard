package com.codexbar.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AiService

@Composable
fun ServiceBrandIcon(
    service: AiService,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val brandColor = Color(service.brandColor)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(brandColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        when (service) {
            AiService.CODEX,
            AiService.CHATGPT_PLUS -> ChatGptMark(color = brandColor, modifier = Modifier.size(size * 0.68f))
            AiService.DEEPSEEK -> DeepSeekWhaleMark(color = brandColor, modifier = Modifier.size(size * 0.72f))
            else -> Text(
                text = service.iconLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = brandColor
            )
        }
    }
}

@Composable
private fun ChatGptMark(
    color: Color,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.105f
        val radius = size.minDimension * 0.24f
        val center = Offset(size.width / 2f, size.height / 2f)
        repeat(6) { index ->
            val angle = Math.toRadians((index * 60f).toDouble())
            val loopCenter = Offset(
                x = center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.17f,
                y = center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.17f
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(loopCenter.x - radius, loopCenter.y - radius * 0.52f),
                size = Size(radius * 1.58f, radius * 1.04f),
                cornerRadius = CornerRadius(radius * 0.52f, radius * 0.52f),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        drawCircle(
            color = color,
            radius = strokeWidth * 0.8f,
            center = center
        )
    }
}

@Composable
private fun DeepSeekWhaleMark(
    color: Color,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        val body = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.55f)
            cubicTo(
                size.width * 0.18f, size.height * 0.22f,
                size.width * 0.58f, size.height * 0.20f,
                size.width * 0.78f, size.height * 0.43f
            )
            cubicTo(
                size.width * 0.96f, size.height * 0.42f,
                size.width * 1.00f, size.height * 0.25f,
                size.width * 0.92f, size.height * 0.12f
            )
            cubicTo(
                size.width * 0.78f, size.height * 0.20f,
                size.width * 0.74f, size.height * 0.32f,
                size.width * 0.79f, size.height * 0.45f
            )
            cubicTo(
                size.width * 0.78f, size.height * 0.74f,
                size.width * 0.44f, size.height * 0.90f,
                size.width * 0.20f, size.height * 0.73f
            )
            cubicTo(
                size.width * 0.07f, size.height * 0.64f,
                size.width * 0.06f, size.height * 0.58f,
                size.width * 0.12f, size.height * 0.55f
            )
            close()
        }
        drawPath(body, color.copy(alpha = 0.95f))
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.035f,
            center = Offset(size.width * 0.37f, size.height * 0.44f)
        )
        drawArc(
            color = Color.White.copy(alpha = 0.95f),
            startAngle = 10f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(size.width * 0.21f, size.height * 0.50f),
            size = Size(size.width * 0.46f, size.height * 0.25f),
            style = Stroke(width = size.minDimension * 0.04f, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 185f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(size.width * 0.06f, size.height * 0.48f),
            size = Size(size.width * 0.25f, size.height * 0.30f),
            style = Stroke(width = size.minDimension * 0.07f, cap = StrokeCap.Round)
        )
        drawOval(
            color = Color.White.copy(alpha = 0.88f),
            topLeft = Offset(size.width * 0.52f, size.height * 0.58f),
            size = Size(size.width * 0.18f, size.height * 0.08f)
        )
    }
}
