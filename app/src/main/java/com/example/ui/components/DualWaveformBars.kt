package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

@Composable
fun DualWaveformBars(
    userAmplitude: Float,
    aiAmplitude: Float,
    rttMs: Long,
    bytesSent: Long,
    bytesReceived: Long,
    sampleRate: Int,
    isBinary: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("dual_waveform_bars"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Channel equalizer rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Voice Channel (Upstream Mic)
                ChannelEqualizer(
                    title = "用户上行 (MIC)",
                    amplitude = userAmplitude,
                    barColor = CyanAccent,
                    icon = Icons.Default.Mic,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // AI Voice Channel (Downstream Speaker)
                ChannelEqualizer(
                    title = "AI下行 (SPK)",
                    amplitude = aiAmplitude,
                    barColor = VioletAccent,
                    icon = Icons.Default.Hearing,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // RTT latency
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (rttMs > 0 && rttMs < 100) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (rttMs > 0) "延迟: ${rttMs}ms" else "延迟: --",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextSecondary
                    )
                }

                // Audio format badge (16k Up / 24k Down)
                Text(
                    text = "↑16k ↓24k · ${if (isBinary) "方案A(裸流)" else "方案B(JSON)"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )

                // Traffic stats
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SyncAlt,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val sentKb = bytesSent / 1024
                    val rcvKb = bytesReceived / 1024
                    Text(
                        text = "↑${sentKb}K ↓${rcvKb}K",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelEqualizer(
    title: String,
    amplitude: Float,
    barColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val barCount = 10
    val multipliers = remember { floatArrayOf(0.4f, 0.7f, 1.0f, 0.85f, 0.6f, 0.9f, 1.1f, 0.75f, 0.5f, 0.3f) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = barColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = barColor
            )
        }

        // Equalizer frequency bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 0 until barCount) {
                val factor = multipliers[i % multipliers.size]
                val targetHeight = (amplitude * factor * 24f).coerceIn(3f, 24f)

                val animHeight = remember { Animatable(3f) }
                LaunchedEffect(targetHeight) {
                    animHeight.animateTo(targetHeight, tween(80, easing = FastOutSlowInEasing))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(animHeight.value.dp)
                        .clip(CircleShape)
                        .background(
                            if (amplitude > 0.05f) barColor else barColor.copy(alpha = 0.25f)
                        )
                )
            }
        }
    }
}
