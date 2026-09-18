package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ConnectionState
import com.example.ui.InteractionState
import com.example.ui.theme.CoralWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

/**
 * Dynamic Call Dock with radiant sonar ripple rings and tactile feedback.
 */
@Composable
fun ControlDock(
    connectionState: ConnectionState,
    interactionState: InteractionState,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    val infiniteTransition = rememberInfiniteTransition(label = "dock_motion")

    // Sonar wave ripple expansion
    val rippleProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonar_1"
    )

    val rippleProgress2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonar_2"
    )

    // Breathing pulse for central button
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val accentColor = when {
        isConnected -> CoralWarning
        isConnecting -> Color(0xFFF59E0B)
        else -> CyanAccent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 18.dp, top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(104.dp),
            contentAlignment = Alignment.Center
        ) {
            // Radiant Sonar Ripples Canvas
            Canvas(modifier = Modifier.size(104.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = 36.dp.toPx()

                if (isConnected || isConnecting) {
                    // Ripple 1
                    val r1 = baseRadius + (rippleProgress1 * 16.dp.toPx())
                    val alpha1 = ((1f - rippleProgress1) * 0.45f).coerceIn(0f, 1f)
                    drawCircle(
                        color = accentColor.copy(alpha = alpha1),
                        radius = r1,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Ripple 2
                    val normProg2 = rippleProgress2 % 1f
                    val r2 = baseRadius + (normProg2 * 16.dp.toPx())
                    val alpha2 = ((1f - normProg2) * 0.45f).coerceIn(0f, 1f)
                    drawCircle(
                        color = accentColor.copy(alpha = alpha2),
                        radius = r2,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                } else {
                    // Ambient idle halo
                    val idleHaloR = baseRadius + 6.dp.toPx() * pulseScale
                    drawCircle(
                        color = CyanAccent.copy(alpha = 0.12f),
                        radius = idleHaloR,
                        center = center
                    )
                }
            }

            // Central Action Button with Dynamic Gradient
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(if (isConnected) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (isConnected) {
                                listOf(CoralWarning, Color(0xFFE11D48))
                            } else if (isConnecting) {
                                listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                            } else {
                                listOf(CyanAccent, VioletAccent)
                            }
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.05f))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onToggleConnection,
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("call_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CallEnd else Icons.Default.Call,
                        contentDescription = if (isConnected) "结束通话" else "开启与小澈对话",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when {
                isConnected -> "通话中 · 随时开口即可打断小澈"
                isConnecting -> "正在连通小澈智能语音..."
                else -> "点击开启与小澈的实时通话"
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            ),
            color = TextSecondary.copy(alpha = 0.85f)
        )
    }
}
