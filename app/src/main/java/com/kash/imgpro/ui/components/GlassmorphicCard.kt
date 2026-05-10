package com.kash.imgpro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kash.imgpro.ui.theme.DarkSurfaceVariant
import com.kash.imgpro.ui.theme.GlassBorder
import com.kash.imgpro.ui.theme.GlassHighlight
import com.kash.imgpro.ui.theme.GlassWhite

/**
 * A glassmorphism-styled card with:
 *  - Semi-transparent frosted surface
 *  - Animated gradient border shimmer
 *  - Press-scale micro-animation
 *  - Haptic feedback on click
 *
 * @param modifier      Modifier chain.
 * @param accentColor   Primary accent for the gradient border.
 * @param secondaryAccent Secondary accent for the gradient border.
 * @param cornerRadius  Corner rounding.
 * @param onClick       Click callback (triggers haptic feedback).
 * @param content       Composable content inside the card.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00E5FF),
    secondaryAccent: Color = Color(0xFFB388FF),
    cornerRadius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // ── Press scale animation ─────────────────────────────────────
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "card_press_scale",
    )

    // ── Shimmer on the gradient border ────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    // ── Pulsing border glow ───────────────────────────────────────
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            // Gradient border with animated shimmer
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.6f * glowAlpha),
                        secondaryAccent.copy(alpha = 0.4f),
                        GlassBorder,
                        accentColor.copy(alpha = 0.5f * glowAlpha),
                    ),
                    start = Offset(shimmerOffset, 0f),
                    end = Offset(shimmerOffset + 600f, 600f),
                ),
                shape = shape,
            )
            // Glass background layers
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassWhite.copy(alpha = 0.14f),
                        DarkSurfaceVariant.copy(alpha = 0.75f),
                    ),
                ),
                shape = shape,
            )
            // Internal top highlight (simulates light reflection)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GlassHighlight.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.4f,
                    ),
                )
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
            .padding(20.dp),
        content = content,
    )
}
