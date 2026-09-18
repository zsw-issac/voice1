package com.example.ui.components

import androidx.compose.animation.AnimatedContent
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
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

/**
 * Concise, tech-savvy single-line display banner.
 * Shows exactly ONE clean, dynamic text line corresponding to the current live dialogue state.
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
    // Determine the active single sentence to display
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("single_subtitle_banner"),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF101726).copy(alpha = 0.65f),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.02f)
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
                transitionSpec = { fadeIn() togetherWith fadeOut() },
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
                        val (tagIcon, tagColor, tagLabel) = when (display.role) {
                            "user" -> Triple(Icons.Default.Mic, CyanAccent, if (display.isLive) "您正在说" else "您")
                            "ai" -> Triple(Icons.Default.SmartToy, VioletAccent, if (display.isLive) "小澈正在回答" else "小澈")
                            else -> Triple(Icons.Default.GraphicEq, CyanAccent.copy(alpha = 0.85f), "全双工实时流")
                        }

                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(tagColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tagIcon,
                                contentDescription = null,
                                tint = tagColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(7.dp))

                        Text(
                            text = tagLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = tagColor
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
