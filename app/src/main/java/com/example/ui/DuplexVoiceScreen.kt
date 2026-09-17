package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.ConnectionState
import com.example.ui.components.ControlDock
import com.example.ui.components.DualWaveformBars
import com.example.ui.components.HistorySheet
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TranscriptView
import com.example.ui.components.VoiceVisualizerOrb
import com.example.ui.theme.CoralWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DuplexVoiceScreen(
    viewModel: DuplexViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }

    // Audio record permission handling
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            viewModel.toggleConnection()
        }
    }

    // Show error toast/snackbar
    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState is ConnectionState.Error) {
            val errorMsg = (uiState.connectionState as ConnectionState.Error).message
            snackbarHostState.showSnackbar(errorMsg)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("duplex_voice_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ControlDock(
                connectionState = uiState.connectionState,
                interactionState = uiState.interactionState,
                isMicMuted = uiState.isMicMuted,
                isSpeakerOn = uiState.isSpeakerOn,
                onToggleConnection = {
                    if (!hasRecordPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleConnection()
                    }
                },
                onToggleMicMute = { viewModel.toggleMicMute() },
                onToggleSpeaker = { viewModel.toggleSpeaker() },
                onBargeIn = { viewModel.triggerBargeIn() },
                onSendTextMessage = { text -> viewModel.sendTextMessage(text) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding()
        ) {
            // Top App Bar
            TopBarHeader(
                connectionState = uiState.connectionState,
                isSimulator = uiState.isSimulatorMode,
                onOpenHistory = { showHistorySheet = true },
                onOpenSettings = { showSettingsDialog = true }
            )

            // Permission Warning Banner if missing RECORD_AUDIO
            AnimatedVisibility(visible = !hasRecordPermission) {
                Surface(
                    color = CoralWarning.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = CoralWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "需要麦克风权限以开启全双工语音交互",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoralWarning
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = CoralWarning),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("授权", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 1. Interactive Voice Visualizer Orb
            VoiceVisualizerOrb(
                interactionState = uiState.interactionState,
                userAmplitude = uiState.userAmplitude,
                aiAmplitude = uiState.aiAmplitude,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Dual Channel Voice Level Indicator
            DualWaveformBars(
                userAmplitude = uiState.userAmplitude,
                aiAmplitude = uiState.aiAmplitude,
                rttMs = uiState.rttMs,
                bytesSent = uiState.bytesSent,
                bytesReceived = uiState.bytesReceived,
                sampleRate = uiState.sampleRate,
                isBinary = uiState.isBinaryMode
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Conversational Transcript View
            TranscriptView(
                messages = uiState.messages,
                currentUserTranscript = uiState.currentUserTranscript,
                currentAiText = uiState.currentAiText,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Modal Dialogs & Sheets
    if (showSettingsDialog) {
        SettingsDialog(
            initialServerUrl = uiState.serverUrl,
            initialIsSimulator = uiState.isSimulatorMode,
            initialSampleRate = uiState.sampleRate,
            initialFrameDurationMs = uiState.frameDurationMs,
            initialIsBinary = uiState.isBinaryMode,
            initialAutoInterrupt = uiState.autoInterrupt,
            initialVadThreshold = uiState.vadThreshold,
            onDismiss = { showSettingsDialog = false },
            onSave = { url, sim, rate, frame, binary, interrupt, vad ->
                viewModel.updateSettings(
                    serverUrl = url,
                    isSimulatorMode = sim,
                    sampleRate = rate,
                    frameDurationMs = frame,
                    isBinaryMode = binary,
                    autoInterrupt = interrupt,
                    vadThreshold = vad
                )
            }
        )
    }

    if (showHistorySheet) {
        HistorySheet(
            conversations = uiState.conversations,
            onDeleteConversation = { id -> viewModel.deleteConversation(id) },
            onClearAll = { viewModel.clearAllHistory() },
            onDismiss = { showHistorySheet = false }
        )
    }
}

@Composable
private fun TopBarHeader(
    connectionState: ConnectionState,
    isSimulator: Boolean,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App title & Connection Status Pill
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "全双工语音助手",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Status Badge
            val (statusText, statusColor) = when {
                isSimulator -> "离线演示" to CyanAccent
                connectionState is ConnectionState.Connected -> "已连通" to EmeraldSuccess
                connectionState is ConnectionState.Connecting -> "连接中..." to Color(0xFFF59E0B)
                connectionState is ConnectionState.Error -> "离线" to CoralWarning
                else -> "待呼叫" to TextSecondary
            }

            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = statusColor
                    )
                }
            }
        }

        // Action Icons
        Row(verticalAlignment = Alignment.CenterVertically) {
            // History button
            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier.testTag("history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "历史对话",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Settings button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
