package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * Clean & minimal Call Dock focusing purely on the primary Call Start/Stop action.
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

    // Ambient breathing / pulsing animation while connected
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "call_pulse"
    )

    // Outer glow pulse
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.05f,
        targetValue = if (isConnected) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(88.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glowing halo when connected
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(CoralWarning.copy(alpha = 0.22f))
                )
            } else if (isConnecting) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(CyanAccent.copy(alpha = 0.22f))
                )
            }

            // Central Call / Hangup Button
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

        Spacer(modifier = Modifier.height(8.dp))

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
