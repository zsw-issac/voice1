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
                    text = "全双工通信与音频配置",
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Mode Toggle: Simulator vs Remote
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                                text = "本地诊断/模拟模式",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "在远端服务端未就绪时测试VAD、打断和音频流",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = isSimulator,
                            onCheckedChange = { isSimulator = it },
                            modifier = Modifier.testTag("simulator_mode_switch")
                        )
                    }
                }

                if (!isSimulator) {
                    // Server WebSocket URL
                    Column {
                        Text(
                            text = "模型服务 WebSocket 地址",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_url_input"),
                            placeholder = { Text("ws://10.0.2.2:8000/ws/audio") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Quick fill buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QuickFillChip(label = "模拟器PC (10.0.2.2)") {
                                serverUrl = "ws://10.0.2.2:8000/ws/audio"
                            }
                            QuickFillChip(label = "局域网默认") {
                                serverUrl = "ws://192.168.1.100:8000/ws/audio"
                            }
                        }
                    }
                }

                // Audio Sample Rate
                Column {
                    Text(
                        text = "音频采样率",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sampleRate == 16000,
                            onClick = { sampleRate = 16000 },
                            label = { Text("16000 Hz (通用标清)") }
                        )
                        FilterChip(
                            selected = sampleRate == 24000,
                            onClick = { sampleRate = 24000 },
                            label = { Text("24000 Hz (高清模型)") }
                        )
                    }
                }

                // Frame Duration
                Column {
                    Text(
                        text = "单帧时长 (延迟粒度)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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
                        FilterChip(
                            selected = frameDurationMs == 100,
                            onClick = { frameDurationMs = 100 },
                            label = { Text("100ms") }
                        )
                    }
                }

                // Payload Format
                Column {
                    Text(
                        text = "传输数据格式",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isBinary,
                            onClick = { isBinary = true },
                            label = { Text("二进制流 (Binary PCM)") }
                        )
                        FilterChip(
                            selected = !isBinary,
                            onClick = { isBinary = false },
                            label = { Text("JSON (Base64)") }
                        )
                    }
                }

                // Auto Barge-in (Barge-in on user voice)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "智能说话打断 (Barge-in)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "AI播放语音时，用户发声自动清空下行缓冲并通知模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = autoInterrupt,
                        onCheckedChange = { autoInterrupt = it }
                    )
                }

                // VAD Sensitivity
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "VAD静音/语音检测灵敏度",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${(vadThreshold * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanAccent
                        )
                    }
                    Slider(
                        value = vadThreshold,
                        onValueChange = { vadThreshold = it },
                        valueRange = 0.02f..0.25f,
                        modifier = Modifier.testTag("vad_slider")
                    )
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
                Text("保存设置")
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
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = CyanAccent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
