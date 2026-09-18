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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/**
 * Organic, highly fluid Voice Visualizer Orb.
 * Employs harmonic fluid wave paths, counter-rotating iridescent ribbons,
 * acoustic shockwave ripples, and reactive orbital motes.
 */
@Composable
fun VoiceVisualizerOrb(
    interactionState: InteractionState,
    userAmplitude: Float,
    aiAmplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_fluid_motion")

    // Continuous wave phase oscillations
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val wavePhase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    // Breathing scale for organic living heartbeat
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Sound shockwave expansion progress (0f -> 1f)
    val shockwaveProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shockwave1"
    )

    val shockwaveProgress2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shockwave2"
    )

    // Smooth amplitude transitions for responsive audio reactive physics
    val smoothUserAmp = remember { Animatable(0f) }
    val smoothAiAmp = remember { Animatable(0f) }

    LaunchedEffect(userAmplitude) {
        smoothUserAmp.animateTo(userAmplitude.coerceIn(0f, 1f), tween(70))
    }
    LaunchedEffect(aiAmplitude) {
        smoothAiAmp.animateTo(aiAmplitude.coerceIn(0f, 1f), tween(70))
    }

    val totalAmp = (smoothUserAmp.value * 1.3f + smoothAiAmp.value * 1.5f).coerceIn(0f, 1.5f)

    // Dynamic state colors
    val primaryGlowColor by animateColorAsState(
        targetValue = when (interactionState) {
            InteractionState.IDLE -> Color(0xFF38BDF8)
            InteractionState.LISTENING -> CyanGlow
            InteractionState.USER_SPEAKING -> Color(0xFF10B981) // Emerald
            InteractionState.THINKING -> Color(0xFFF59E0B) // Amber
            InteractionState.AI_SPEAKING -> VioletGlow
            InteractionState.INTERRUPTED -> Color(0xFFF43F5E) // Coral
        },
        animationSpec = tween(350),
        label = "glow_color"
    )

    val secondaryGlowColor by animateColorAsState(
        targetValue = when (interactionState) {
            InteractionState.IDLE -> Color(0xFF6366F1)
            InteractionState.LISTENING -> CyanAccent
            InteractionState.USER_SPEAKING -> Color(0xFF059669)
            InteractionState.THINKING -> Color(0xFFD97706)
            InteractionState.AI_SPEAKING -> VioletAccent
            InteractionState.INTERRUPTED -> Color(0xFFE11D48)
        },
        animationSpec = tween(350),
        label = "secondary_color"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_visualizer_orb"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center
        ) {
            // High-fidelity Kinetic Canvas
            Canvas(modifier = Modifier.size(190.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = (size.width * 0.28f) * breathingScale

                // 1. Acoustic Expanding Shockwaves when active
                val isSpeaking = totalAmp > 0.04f || interactionState == InteractionState.AI_SPEAKING || interactionState == InteractionState.USER_SPEAKING
                if (isSpeaking || interactionState == InteractionState.LISTENING) {
                    val waveAmpBoost = (totalAmp * 25f).coerceAtLeast(6f)

                    // Shockwave 1
                    val sw1R = baseRadius * (1.0f + shockwaveProgress1 * 0.65f) + waveAmpBoost
                    val sw1Alpha = ((1f - shockwaveProgress1) * 0.45f * (0.4f + totalAmp * 0.6f)).coerceIn(0f, 1f)
                    drawCircle(
                        color = primaryGlowColor.copy(alpha = sw1Alpha),
                        radius = sw1R,
                        center = center,
                        style = Stroke(width = (2.5f * (1f - shockwaveProgress1)).coerceAtLeast(0.8f))
                    )

                    // Shockwave 2
                    val normProg2 = (shockwaveProgress2 % 1f)
                    val sw2R = baseRadius * (1.0f + normProg2 * 0.65f) + waveAmpBoost
                    val sw2Alpha = ((1f - normProg2) * 0.45f * (0.4f + totalAmp * 0.6f)).coerceIn(0f, 1f)
                    drawCircle(
                        color = secondaryGlowColor.copy(alpha = sw2Alpha),
                        radius = sw2R,
                        center = center,
                        style = Stroke(width = (2.5f * (1f - normProg2)).coerceAtLeast(0.8f))
                    )
                }

                // 2. Ambient background aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlowColor.copy(alpha = (0.22f + totalAmp * 0.35f).coerceAtMost(0.6f)),
                            secondaryGlowColor.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.width / 2f
                    ),
                    radius = size.width / 2f,
                    center = center
                )

                // 3. Fluid Wave Deformation Paths (Harmonic Closed Loops)
                val effectiveAmplitude = 4f + (totalAmp * 32f)

                // Layer A: Secondary Outer Ribbon (Counter-clockwise rotation)
                val outerPath = createFluidWavePath(
                    center = center,
                    baseRadius = baseRadius * 1.14f,
                    amplitude = effectiveAmplitude * 0.75f,
                    phase = wavePhase2,
                    harmonics = 5,
                    points = 48
                )
                drawPath(
                    path = outerPath,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            secondaryGlowColor.copy(alpha = 0.65f),
                            primaryGlowColor.copy(alpha = 0.85f),
                            secondaryGlowColor.copy(alpha = 0.25f),
                            secondaryGlowColor.copy(alpha = 0.65f)
                        ),
                        center = center
                    ),
                    style = Stroke(width = 3.5f + (totalAmp * 4.5f))
                )

                // Layer B: Primary Outer Fluid Ribbon (Clockwise rotation)
                val primaryPath = createFluidWavePath(
                    center = center,
                    baseRadius = baseRadius * 0.98f,
                    amplitude = effectiveAmplitude,
                    phase = wavePhase1,
                    harmonics = 4,
                    points = 48
                )
                drawPath(
                    path = primaryPath,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryGlowColor.copy(alpha = 0.95f),
                            secondaryGlowColor.copy(alpha = 0.75f),
                            primaryGlowColor.copy(alpha = 0.35f),
                            primaryGlowColor.copy(alpha = 0.95f)
                        ),
                        center = center
                    ),
                    style = Stroke(width = 4.0f + (totalAmp * 6f))
                )

                // Layer C: Inner Liquid Plasma Core (Harmonic multi-frequency fill)
                val corePath = createFluidWavePath(
                    center = center,
                    baseRadius = baseRadius * 0.75f,
                    amplitude = effectiveAmplitude * 0.5f,
                    phase = wavePhase3,
                    harmonics = 3,
                    points = 36
                )
                drawPath(
                    path = corePath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlowColor.copy(alpha = 0.85f),
                            secondaryGlowColor.copy(alpha = 0.55f),
                            Color(0xFF0A0F1D).copy(alpha = 0.95f)
                        ),
                        center = center - Offset(baseRadius * 0.2f, baseRadius * 0.2f),
                        radius = baseRadius * 0.9f
                    )
                )

                // 4. Orbital Floating Luminescent Motes / Energy Fireflies
                val moteCount = 8
                for (i in 0 until moteCount) {
                    val angle = (wavePhase1 * 1.5f + i * (2f * PI.toFloat() / moteCount))
                    val orbitOffset = sin(wavePhase2 * 2f + i) * 8f
                    val orbitRadius = (baseRadius * 1.28f + (totalAmp * 18f)) + orbitOffset
                    val moteX = center.x + orbitRadius * cos(angle)
                    val moteY = center.y + orbitRadius * sin(angle)
                    val moteAlpha = 0.45f + 0.45f * sin(wavePhase3 + i)

                    drawCircle(
                        color = primaryGlowColor.copy(alpha = moteAlpha.coerceIn(0.2f, 1f)),
                        radius = 2.5f + (totalAmp * 2.5f),
                        center = Offset(moteX, moteY)
                    )
                }
            }

            // Central Interactive Status Icon with Dynamic Spring Scale
            val centerScale = (1f + totalAmp * 0.22f).coerceIn(0.95f, 1.35f)
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
                    .size(50.dp)
                    .scale(centerScale)
                    .clip(CircleShape)
                    .background(Color(0xFF0D1424).copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = centerIcon,
                    contentDescription = interactionState.label,
                    tint = primaryGlowColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // State indicator badge with glowing dot and glassmorphic border
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, primaryGlowColor.copy(alpha = 0.35f)),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(primaryGlowColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = interactionState.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = primaryGlowColor
                )
            }
        }
    }
}

/**
 * Generates an organic harmonic closed fluid path using multi-frequency sine wave distortion.
 */
private fun createFluidWavePath(
    center: Offset,
    baseRadius: Float,
    amplitude: Float,
    phase: Float,
    harmonics: Int,
    points: Int
): Path {
    val path = Path()
    for (i in 0..points) {
        val angle = (i.toFloat() / points) * (2f * PI.toFloat())
        // Multi-frequency harmonic distortion
        val wave1 = sin(angle * harmonics + phase)
        val wave2 = cos(angle * (harmonics + 2) - phase * 1.4f)
        val wave3 = sin(angle * 2f + phase * 0.7f)
        val deltaR = (wave1 * 0.55f + wave2 * 0.32f + wave3 * 0.20f) * amplitude
        val r = (baseRadius + deltaR).coerceAtLeast(8f)

        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    return path
}
