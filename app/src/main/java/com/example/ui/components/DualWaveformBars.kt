package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import kotlin.math.PI
import kotlin.math.sin

/**
 * Dual channel voice level indicator with rhythmic, living equalizer animation.
 * Features ambient harmonic breathing so the audio channels are continuously alive.
 */
@Composable
fun DualWaveformBars(
    userAmplitude: Float,
    aiAmplitude: Float,
    rttMs: Long,
    bytesSent: Long = 0L,
    bytesReceived: Long = 0L,
    sampleRate: Int = 16000,
    isBinary: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .testTag("dual_waveform_bars"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // User Mic Audio Pill
            AudioChannelPill(
                label = "我的声音",
                amplitude = userAmplitude,
                accentColor = CyanAccent,
                icon = Icons.Default.Mic,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Latency Indicator with dynamic status dot
            if (rttMs > 0) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0C1220).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (rttMs < 120) Color(0xFF10B981) else Color(0xFFF59E0B))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${rttMs}ms",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // XiaoChe Voice Audio Pill
            AudioChannelPill(
                label = "小澈应答",
                amplitude = aiAmplitude,
                accentColor = VioletAccent,
                icon = Icons.Default.Hearing,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AudioChannelPill(
    label: String,
    amplitude: Float,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val animatedAmp = remember { Animatable(0f) }
    LaunchedEffect(amplitude) {
        animatedAmp.animateTo(
            targetValue = amplitude.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing)
        )
    }

    // Idle ambient living rhythm
    val infiniteTransition = rememberInfiniteTransition(label = "eq_ambient")
    val rhythmPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rhythm"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))

        // 6 Living Equalizer Rhythm Bars
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val weights = listOf(0.40f, 0.75f, 1.0f, 0.85f, 0.60f, 0.35f)
            val barCount = weights.size
            for (i in 0 until barCount) {
                val idleWave = (sin(rhythmPhase + i * 0.9f) * 0.5f + 0.5f) * 3f
                val activeAmpBoost = animatedAmp.value * 22f * weights[i]
                val currentHeight = (3.5f + idleWave + activeAmpBoost).dp

                val isHighVolume = animatedAmp.value > 0.08f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(currentHeight)
                        .clip(CircleShape)
                        .background(
                            brush = if (isHighVolume) {
                                Brush.verticalGradient(
                                    listOf(accentColor, accentColor.copy(alpha = 0.6f))
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(accentColor.copy(alpha = 0.38f), accentColor.copy(alpha = 0.16f))
                                )
                            }
                        )
                )
            }
        }
    }
}
