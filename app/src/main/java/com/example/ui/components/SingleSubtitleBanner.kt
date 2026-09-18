package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MessageEntity
import com.example.network.ConnectionState
import com.example.ui.InteractionState
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VioletAccent
import kotlin.math.sin

/**
 * Single-line focused dialogue banner with live animated micro-equalizer and glowing glass border.
 */
@Composable
fun SingleSubtitleBanner(
    connectionState: ConnectionState,
    interactionState: InteractionState,
    messages: List<MessageEntity>,
    currentUserTranscript: String,
    currentAiText: String,
    modifier: Modifier = Modifier
) {
    data class DisplayState(
        val role: String, // "ai", "user", or "system"
        val text: String,
        val isLive: Boolean
    )

    val currentDisplay: DisplayState = when {
        currentUserTranscript.isNotEmpty() -> {
            DisplayState("user", currentUserTranscript, true)
        }
        currentAiText.isNotEmpty() -> {
            DisplayState("ai", currentAiText, true)
        }
        messages.isNotEmpty() -> {
            val last = messages.last()
            DisplayState(if (last.role == "assistant") "ai" else "user", last.content, false)
        }
        connectionState is ConnectionState.Connected -> {
            when (interactionState) {
                InteractionState.USER_SPEAKING -> DisplayState("user", "正在聆听您的声音...", true)
                InteractionState.THINKING -> DisplayState("ai", "小澈正在思考回复...", true)
                InteractionState.AI_SPEAKING -> DisplayState("ai", "小澈正在回答...", true)
                InteractionState.INTERRUPTED -> DisplayState("system", "已打断，请随时对小澈继续说", false)
                else -> DisplayState("system", "小澈已就绪，随时开口即可对话", false)
            }
        }
        connectionState is ConnectionState.Connecting -> {
            DisplayState("system", "正在连接小澈全双工语音服务...", true)
        }
        else -> {
            DisplayState("system", "点击下方通话按钮，开启与小澈的对话", false)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "banner_ambient")
    val borderGlowPhase by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_glow"
    )

    val tagColor = when (currentDisplay.role) {
        "user" -> CyanAccent
        "ai" -> VioletAccent
        else -> CyanAccent.copy(alpha = 0.85f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("single_subtitle_banner"),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0F1626).copy(alpha = 0.70f),
        border = BorderStroke(
            1.2.dp,
            Brush.verticalGradient(
                listOf(
                    tagColor.copy(alpha = borderGlowPhase),
                    Color.White.copy(alpha = 0.04f)
                )
            )
        ),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentDisplay,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "subtitle_fade"
            ) { display ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Role / Status indicator tag
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val (tagIcon, roleColor, tagLabel) = when (display.role) {
                            "user" -> Triple(Icons.Default.Mic, CyanAccent, if (display.isLive) "您正在说" else "您")
                            "ai" -> Triple(Icons.Default.SmartToy, VioletAccent, if (display.isLive) "小澈正在回答" else "小澈")
                            else -> Triple(Icons.Default.GraphicEq, CyanAccent.copy(alpha = 0.85f), "全双工实时流")
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(roleColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tagIcon,
                                contentDescription = null,
                                tint = roleColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(7.dp))

                        Text(
                            text = tagLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = roleColor
                        )

                        // If currently live, show 3-bar animated dancing micro wave
                        if (display.isLive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            LiveMicroWave(color = roleColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Text Content (Clean, single focused message)
                    Text(
                        text = display.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Normal,
                            lineHeight = 28.sp,
                            fontSize = 17.5.sp
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 3-bar animated dancing micro-wave indicating active speech.
 */
@Composable
private fun LiveMicroWave(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "micro_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 3) {
            val waveFactor = (sin(phase + i * 1.5f) * 0.5f + 0.5f)
            val barH = 4.dp + 7.dp * waveFactor
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(barH)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
