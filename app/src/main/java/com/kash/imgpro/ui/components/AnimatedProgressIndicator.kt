package com.kash.imgpro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kash.imgpro.ui.theme.ElectricCyan
import com.kash.imgpro.ui.theme.VividViolet

/**
 * Animated circular progress indicator with:
 *  - Gradient sweep arc
 *  - Pulsing glow ring
 *  - Percentage text overlay with animated counter
 *
 * @param progress      0f–1f progress value.
 * @param modifier      Modifier chain.
 * @param size          Diameter of the indicator.
 * @param strokeWidth   Arc stroke width.
 * @param primaryColor  Start color of the gradient arc.
 * @param secondaryColor End color of the gradient arc.
 * @param trackColor    Background track ring color.
 * @param showPercentage Whether to display the percentage text.
 */
@Composable
fun AnimatedProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
    primaryColor: Color = ElectricCyan,
    secondaryColor: Color = VividViolet,
    trackColor: Color = Color(0xFF252529),
    showPercentage: Boolean = true,
) {
    // Smooth progress animation
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "progress_anim",
    )

    // Pulsing glow
    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val canvasSize = this.size
            val stroke = strokeWidth.toPx()
            val arcSize = Size(canvasSize.width - stroke, canvasSize.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Glow ring (pulsing)
            if (animatedProgress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = glowAlpha),
                            secondaryColor.copy(alpha = glowAlpha * 0.5f),
                            primaryColor.copy(alpha = glowAlpha),
                        ),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke + 6.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Progress arc (gradient)
            if (animatedProgress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryColor,
                            secondaryColor,
                            primaryColor,
                        ),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        // Percentage text
        if (showPercentage) {
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.22f).sp,
                ),
                color = primaryColor,
            )
        }
    }
}
