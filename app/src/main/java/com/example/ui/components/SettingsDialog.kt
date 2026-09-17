package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsDialog(
    initialServerUrl: String,
    initialIsSimulator: Boolean,
    initialSampleRate: Int,
    initialFrameDurationMs: Int,
    initialIsBinary: Boolean,
    initialAutoInterrupt: Boolean,
    initialVadThreshold: Float,
    onDismiss: () -> Unit,
    onSave: (
        serverUrl: String,
        isSimulator: Boolean,
        sampleRate: Int,
        frameDurationMs: Int,
        isBinary: Boolean,
        autoInterrupt: Boolean,
        vadThreshold: Float
    ) -> Unit
) {
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var isSimulator by remember { mutableStateOf(initialIsSimulator) }
    var sampleRate by remember { mutableIntStateOf(initialSampleRate) }
    var frameDurationMs by remember { mutableIntStateOf(initialFrameDurationMs) }
    var isBinary by remember { mutableStateOf(initialIsBinary) }
    var autoInterrupt by remember { mutableStateOf(initialAutoInterrupt) }
    var vadThreshold by remember { mutableFloatStateOf(initialVadThreshold) }

    var showDeveloperOptions by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = CyanAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "通话与服务设置",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("settings_dialog_content"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Server Address
                Column {
                    Text(
                        text = "语音模型服务地址",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input"),
                        placeholder = { Text("wss://voice.zswen.online/ws/duplex") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuickFillChip(label = "线上生产地址") {
                            serverUrl = "wss://voice.zswen.online/ws/duplex"
                        }
                        QuickFillChip(label = "备用路由") {
                            serverUrl = "wss://voice.zswen.online/v1/realtime"
                        }
                    }
                }

                // 2. Auto Barge-in (智能打断)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "智能发声打断 (Barge-in)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "AI说话时，您开口说话自动打断并切换为倾听",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = autoInterrupt,
                            onCheckedChange = { autoInterrupt = it }
                        )
                    }
                }

                // 3. VAD Sensitivity (语音感应灵敏度)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "麦克风发声感应灵敏度",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (vadThreshold < 0.06f) "高灵敏" else if (vadThreshold < 0.12f) "标准" else "低灵敏(防噪)",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanAccent
                        )
                    }
                    Slider(
                        value = vadThreshold,
                        onValueChange = { vadThreshold = it },
                        valueRange = 0.02f..0.20f,
                        modifier = Modifier.testTag("vad_slider")
                    )
                }

                // 4. Advanced / Developer Options Toggle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.clickable { showDeveloperOptions = !showDeveloperOptions }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = TextSecondary
                            )
                            Text(
                                text = "高级音视频参数",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = if (showDeveloperOptions) "收起 ▲" else "展开 ▼",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent
                        )
                    }
                }

                // Expandable Developer Options
                if (showDeveloperOptions) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Offline simulator toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "离线演示体验模式",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Switch(
                                checked = isSimulator,
                                onCheckedChange = { isSimulator = it }
                            )
                        }

                        // Payload Format
                        Column {
                            Text(
                                text = "音频传输编码方案",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = isBinary,
                                    onClick = { isBinary = true },
                                    label = { Text("方案A (二进制裸流)") }
                                )
                                FilterChip(
                                    selected = !isBinary,
                                    onClick = { isBinary = false },
                                    label = { Text("方案B (Base64 JSON)") }
                                )
                            }
                        }

                        // Frame Duration
                        Column {
                            Text(
                                text = "帧长粒度",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = frameDurationMs == 20,
                                    onClick = { frameDurationMs = 20 },
                                    label = { Text("20ms") }
                                )
                                FilterChip(
                                    selected = frameDurationMs == 40,
                                    onClick = { frameDurationMs = 40 },
                                    label = { Text("40ms (推荐)") }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        serverUrl,
                        isSimulator,
                        sampleRate,
                        frameDurationMs,
                        isBinary,
                        autoInterrupt,
                        vadThreshold
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun QuickFillChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = CyanAccent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
