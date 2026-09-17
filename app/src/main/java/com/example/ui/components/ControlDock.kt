package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.VioletAccent

@Composable
fun ControlDock(
    connectionState: ConnectionState,
    interactionState: InteractionState,
    isMicMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleConnection: () -> Unit,
    onToggleMicMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onBargeIn: () -> Unit,
    onSendTextMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    var showTextInput by remember { mutableStateOf(false) }
    var textInputContent by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dock")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("control_dock"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Optional text message input bar
            AnimatedVisibility(visible = showTextInput) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInputContent,
                        onValueChange = { textInputContent = it },
                        placeholder = { Text("输入文本发送给大模型...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("text_message_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (textInputContent.isNotBlank()) {
                                onSendTextMessage(textInputContent)
                                textInputContent = ""
                                showTextInput = false
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CyanAccent)
                            .testTag("send_text_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "发送",
                            tint = Color.Black
                        )
                    }
                }
            }

            // Primary control actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Mic Mute / Unmute button
                IconButton(
                    onClick = onToggleMicMute,
                    enabled = isConnected,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .testTag("mic_mute_button"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isMicMuted) CoralWarning.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isMicMuted) CoralWarning else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMicMuted) "麦克风已静音" else "麦克风正常",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 2. Instant Barge-in (打断) button
                val canInterrupt = isConnected && (interactionState == InteractionState.AI_SPEAKING || interactionState == InteractionState.THINKING)
                Button(
                    onClick = onBargeIn,
                    enabled = isConnected,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canInterrupt) CoralWarning else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (canInterrupt) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("barge_in_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PanTool,
                        contentDescription = "打断",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "打断",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // 3. Central Call / Connect Button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .scale(if (isConnected) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (isConnected) {
                                    listOf(CoralWarning, Color(0xFFE11D48))
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
                            .size(68.dp)
                            .testTag("call_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CallEnd else Icons.Default.Call,
                            contentDescription = if (isConnected) "挂断通话" else "开启通话",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // 4. Speakerphone Toggle button
                IconButton(
                    onClick = onToggleSpeaker,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .testTag("speaker_toggle_button"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSpeakerOn) CyanGlow else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.Hearing,
                        contentDescription = if (isSpeakerOn) "当前为扬声器模式" else "当前为听筒模式",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 5. Text Input toggle
                IconButton(
                    onClick = { showTextInput = !showTextInput },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .testTag("text_input_toggle_button"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (showTextInput) CyanAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (showTextInput) CyanAccent else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "键盘文本输入",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
