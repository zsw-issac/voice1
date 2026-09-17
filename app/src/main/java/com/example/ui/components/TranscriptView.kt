package com.example.ui.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MessageEntity
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

@Composable
fun TranscriptView(
    messages: List<MessageEntity>,
    currentUserTranscript: String,
    currentAiText: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message or live stream
    LaunchedEffect(messages.size, currentUserTranscript, currentAiText) {
        val totalCount = messages.size +
                (if (currentUserTranscript.isNotEmpty()) 1 else 0) +
                (if (currentAiText.isNotEmpty()) 1 else 0)
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("transcript_view"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 1.dp
    ) {
        if (messages.isEmpty() && currentUserTranscript.isEmpty() && currentAiText.isEmpty()) {
            // Friendly Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = CyanAccent.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "全双工实时会话就绪",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "点击下方按钮开启语音连接，开始与大模型全双工对话",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Completed historical messages
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        role = msg.role,
                        content = msg.content,
                        wasInterrupted = msg.wasInterrupted
                    )
                }

                // Streaming user live input bubble
                if (currentUserTranscript.isNotEmpty()) {
                    item(key = "live_user") {
                        MessageBubble(
                            role = "user",
                            content = currentUserTranscript,
                            isStreaming = true
                        )
                    }
                }

                // Streaming AI live response bubble
                if (currentAiText.isNotEmpty()) {
                    item(key = "live_ai") {
                        MessageBubble(
                            role = "assistant",
                            content = currentAiText,
                            isStreaming = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    role: String,
    content: String,
    wasInterrupted: Boolean = false,
    isStreaming: Boolean = false
) {
    val isUser = role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(VioletAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "AI",
                    tint = VioletAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            color = if (isUser) {
                CyanAccent.copy(alpha = 0.15f)
            } else {
                VioletAccent.copy(alpha = 0.15f)
            },
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp,
                        color = TextPrimary
                    )
                )

                if (wasInterrupted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "已被用户打断",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF43F5E)
                        )
                    }
                } else if (isStreaming) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isUser) "识别中..." else "生成中...",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) CyanAccent else VioletAccent
                    )
                }
            }
        }
    }
}
