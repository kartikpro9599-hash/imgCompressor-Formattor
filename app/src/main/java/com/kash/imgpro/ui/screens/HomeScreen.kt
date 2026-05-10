package com.kash.imgpro.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kash.imgpro.ui.components.GlassmorphicCard
import com.kash.imgpro.ui.theme.CoralPink
import com.kash.imgpro.ui.theme.DeepCharcoal
import com.kash.imgpro.ui.theme.ElectricCyan
import com.kash.imgpro.ui.theme.TextSecondary
import com.kash.imgpro.ui.theme.TextTertiary
import com.kash.imgpro.ui.theme.VividViolet
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Home dashboard — two glassmorphic feature cards over an animated
 * particle background with a gradient title.
 */
@Composable
fun HomeScreen(
    onNavigateToCompressor: () -> Unit,
    onNavigateToConverter: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // ── Animated particle background ──────────────────────────
        ParticleBackground()

        // ── Content ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ── App title with gradient ───────────────────────────
            Text(
                text = "Image Compressor",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    brush = Brush.linearGradient(
                        colors = listOf(ElectricCyan, VividViolet, CoralPink),
                    ),
                ),
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "& Converter",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(
                        colors = listOf(VividViolet, CoralPink),
                    ),
                ),
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Professional Image Toolkit",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Feature cards ─────────────────────────────────────
            FeatureCard(
                title = "Smart Compressor",
                description = "Optimize images to your target file size with intelligent quality adjustment",
                icon = Icons.Rounded.Compress,
                accentColor = ElectricCyan,
                secondaryAccent = VividViolet,
                onClick = onNavigateToCompressor,
            )

            Spacer(modifier = Modifier.height(20.dp))

            FeatureCard(
                title = "Format Converter",
                description = "Convert images between JPG, PNG, WEBP, and PDF formats instantly",
                icon = Icons.Rounded.SwapHoriz,
                accentColor = VividViolet,
                secondaryAccent = CoralPink,
                onClick = onNavigateToConverter,
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// =====================================================================
// Feature Card
// =====================================================================

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    secondaryAccent: Color,
    onClick: () -> Unit,
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = accentColor,
        secondaryAccent = secondaryAccent,
        onClick = onClick,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 22.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Tap to open →",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// =====================================================================
// Particle Background
// =====================================================================

private data class Particle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val speed: Float,
    val angle: Float,
)

@Composable
private fun ParticleBackground() {
    val particles = remember {
        List(35) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                radius = Random.nextFloat() * 2.5f + 0.5f,
                color = listOf(
                    ElectricCyan.copy(alpha = 0.15f),
                    VividViolet.copy(alpha = 0.12f),
                    CoralPink.copy(alpha = 0.10f),
                ).random(),
                speed = Random.nextFloat() * 0.3f + 0.1f,
                angle = Random.nextFloat() * 360f,
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particle_time",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Deep background
        drawRect(color = DeepCharcoal)

        // Subtle radial gradient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricCyan.copy(alpha = 0.04f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.3f, size.height * 0.2f),
                radius = size.width * 0.6f,
            ),
            radius = size.width * 0.6f,
            center = Offset(size.width * 0.3f, size.height * 0.2f),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VividViolet.copy(alpha = 0.03f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.8f, size.height * 0.7f),
                radius = size.width * 0.5f,
            ),
            radius = size.width * 0.5f,
            center = Offset(size.width * 0.8f, size.height * 0.7f),
        )

        // Floating particles
        particles.forEach { p ->
            val radians = Math.toRadians((p.angle + time * p.speed).toDouble())
            val offsetX = (cos(radians) * 20f).toFloat()
            val offsetY = (sin(radians) * 20f).toFloat()

            drawCircle(
                color = p.color,
                radius = p.radius.dp.toPx(),
                center = Offset(
                    x = p.x * size.width + offsetX,
                    y = p.y * size.height + offsetY,
                ),
            )
        }
    }
}
