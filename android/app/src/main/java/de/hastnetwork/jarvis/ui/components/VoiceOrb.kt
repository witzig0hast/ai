package de.hastnetwork.jarvis.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

enum class VoiceOrbState { IDLE, LISTENING, SPEAKING }

/**
 * Calm animated orb used as the Voice screen's listening/speaking
 * indicator. Deliberately simple (a breathing radial-gradient circle) -
 * no 3D/particle effects, matching the "clean Google-style" visual
 * direction from the rest of the app.
 */
@Composable
fun VoiceOrb(state: VoiceOrbState, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")

    // Slow, calm breathing while idle/listening; a bit faster/bigger while speaking.
    val periodMillis = if (state == VoiceOrbState.SPEAKING) 900 else 1800
    val minScale = if (state == VoiceOrbState.IDLE) 0.92f else 0.85f
    val maxScale = if (state == VoiceOrbState.SPEAKING) 1.15f else 1.05f

    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMillis, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice_orb_scale",
    )

    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Canvas(modifier = modifier.size(180.dp)) {
        drawOrb(scale = scale, primary = primary, primaryContainer = primaryContainer)
    }
}

private fun DrawScope.drawOrb(
    scale: Float,
    primary: androidx.compose.ui.graphics.Color,
    primaryContainer: androidx.compose.ui.graphics.Color,
) {
    val radius = (size.minDimension / 2f) * scale
    val brush = Brush.radialGradient(
        colors = listOf(primary.copy(alpha = 0.9f), primaryContainer.copy(alpha = 0.35f)),
        center = center,
        radius = radius.coerceAtLeast(1f),
    )
    drawCircle(brush = brush, radius = radius, center = center)
}
