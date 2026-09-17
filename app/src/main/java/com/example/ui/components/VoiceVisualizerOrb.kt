package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MotionPhotosPaused
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.InteractionState
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.VioletAccent
import com.example.ui.theme.VioletGlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceVisualizerOrb(
    interactionState: InteractionState,
    userAmplitude: Float,
    aiAmplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Smooth amplitude transitions
    val smoothUserAmp = remember { Animatable(0f) }
    val smoothAiAmp = remember { Animatable(0f) }

    LaunchedEffect(userAmplitude) {
        smoothUserAmp.animateTo(userAmplitude, tween(100))
    }
    LaunchedEffect(aiAmplitude) {
        smoothAiAmp.animateTo(aiAmplitude, tween(100))
    }

    // Dynamic state colors
    val primaryGlowColor by animateColorAsState(
        targetValue = when (interactionState) {
            InteractionState.IDLE -> Color(0xFF334155)
            InteractionState.LISTENING -> CyanGlow
            InteractionState.USER_SPEAKING -> Color(0xFF34D399) // Emerald
            InteractionState.THINKING -> Color(0xFFF59E0B) // Amber
            InteractionState.AI_SPEAKING -> VioletGlow
            InteractionState.INTERRUPTED -> Color(0xFFF43F5E) // Coral
        },
        animationSpec = tween(400),
        label = "glow_color"
    )

    val secondaryGlowColor by animateColorAsState(
        targetValue = when (interactionState) {
            InteractionState.IDLE -> Color(0xFF1E293B)
            InteractionState.LISTENING -> CyanAccent
            InteractionState.USER_SPEAKING -> Color(0xFF059669)
            InteractionState.THINKING -> Color(0xFFD97706)
            InteractionState.AI_SPEAKING -> VioletAccent
            InteractionState.INTERRUPTED -> Color(0xFFE11D48)
        },
        animationSpec = tween(400),
        label = "secondary_color"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_visualizer_orb"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(230.dp),
            contentAlignment = Alignment.Center
        ) {
            // Particle & Wave Orb Canvas
            Canvas(modifier = Modifier.size(220.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.width * 0.35f * breathingScale

                // Multi-layer dynamic energy modulation
                val activeModulation = (smoothUserAmp.value * 35f) + (smoothAiAmp.value * 45f)
                val currentRadius = baseRadius + activeModulation

                // 1. Ambient outer aura gradient
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlowColor.copy(alpha = if (interactionState == InteractionState.IDLE) 0.1f else 0.35f),
                            secondaryGlowColor.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.width / 2f
                    ),
                    radius = size.width / 2f
                )

                // 2. Dual undulating wave rings
                val waveCount = 8
                val rad = currentRadius
                val waveOffsetAngle = (rotationAngle * PI / 180f).toFloat()

                for (ring in 0..2) {
                    val ringRadius = rad * (0.85f + ring * 0.12f)
                    val alpha = (0.5f - ring * 0.12f).coerceAtLeast(0.1f)
                    val strokeWidth = (2.5f + (smoothUserAmp.value + smoothAiAmp.value) * 6f)

                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                primaryGlowColor.copy(alpha = alpha),
                                secondaryGlowColor.copy(alpha = alpha * 0.5f),
                                primaryGlowColor.copy(alpha = alpha)
                            ),
                            center = center
                        ),
                        radius = ringRadius,
                        style = Stroke(width = strokeWidth)
                    )
                }

                // 3. Central glowing orb core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlowColor.copy(alpha = 0.9f),
                            secondaryGlowColor.copy(alpha = 0.7f),
                            Color(0xFF0F172A)
                        ),
                        center = center - Offset(currentRadius * 0.25f, currentRadius * 0.25f),
                        radius = currentRadius
                    ),
                    radius = currentRadius * 0.72f
                )

                // 4. Harmonic orbital nodes
                if (interactionState != InteractionState.IDLE) {
                    for (i in 0 until 6) {
                        val angle = (rotationAngle * 2.0f + i * 60f) * (PI / 180f).toFloat()
                        val orbitR = currentRadius * 0.95f
                        val nodeX = center.x + orbitR * cos(angle)
                        val nodeY = center.y + orbitR * sin(angle)
                        drawCircle(
                            color = primaryGlowColor,
                            radius = 3.5f + (smoothAiAmp.value * 4f),
                            center = Offset(nodeX, nodeY)
                        )
                    }
                }
            }

            // Center state Icon
            val centerIcon = when (interactionState) {
                InteractionState.IDLE -> Icons.Default.GraphicEq
                InteractionState.LISTENING -> Icons.Default.Mic
                InteractionState.USER_SPEAKING -> Icons.Default.Mic
                InteractionState.THINKING -> Icons.Default.Psychology
                InteractionState.AI_SPEAKING -> Icons.Default.VolumeUp
                InteractionState.INTERRUPTED -> Icons.Default.MotionPhotosPaused
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = centerIcon,
                    contentDescription = interactionState.label,
                    tint = primaryGlowColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // State indicator badge
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
            tonalElevation = 4.dp
        ) {
            Text(
                text = interactionState.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                color = primaryGlowColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}
