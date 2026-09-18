package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.network.ConnectionState
import com.example.ui.components.ControlDock
import com.example.ui.components.DualWaveformBars
import com.example.ui.components.HistorySheet
import com.example.ui.components.SettingsDialog
import com.example.ui.components.SingleSubtitleBanner
import com.example.ui.components.VoiceVisualizerOrb
import com.example.ui.theme.CoralWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

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
    LaunchedEffect(uiState.connectionState, uiState.errorMessage) {
        if (uiState.connectionState is ConnectionState.Error) {
            val errorMsg = (uiState.connectionState as ConnectionState.Error).message
            snackbarHostState.showSnackbar(errorMsg)
        } else if (!uiState.errorMessage.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(uiState.errorMessage ?: "")
            viewModel.clearError()
        }
    }

    // Ambient drifting aurora background transition
    val backgroundTransition = rememberInfiniteTransition(label = "aurora_ambient")
    val auroraOffsetX by backgroundTransition.animateFloat(
        initialValue = -120f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_x"
    )
    val auroraOffsetY by backgroundTransition.animateFloat(
        initialValue = -90f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_y"
    )
    val auroraIntensity by backgroundTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_intensity"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("duplex_voice_screen"),
        containerColor = Color(0xFF080C14),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ControlDock(
                connectionState = uiState.connectionState,
                interactionState = uiState.interactionState,
                onToggleConnection = {
                    if (!hasRecordPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleConnection()
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            when (uiState.interactionState) {
                                InteractionState.USER_SPEAKING -> Color(0xFF047857).copy(alpha = (auroraIntensity + 0.08f).coerceAtMost(0.5f))
                                InteractionState.AI_SPEAKING -> Color(0xFF7C3AED).copy(alpha = (auroraIntensity + 0.12f).coerceAtMost(0.55f))
                                InteractionState.LISTENING -> Color(0xFF0284C7).copy(alpha = auroraIntensity)
                                InteractionState.THINKING -> Color(0xFFD97706).copy(alpha = auroraIntensity)
                                else -> Color(0xFF1E293B).copy(alpha = auroraIntensity * 0.75f)
                            },
                            Color(0xFF080C14)
                        ),
                        center = Offset(500f + auroraOffsetX, 380f + auroraOffsetY),
                        radius = 1250f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .statusBarsPadding()
            ) {
                // Top App Bar with Voice Mode Switcher
                TopBarHeader(
                    connectionState = uiState.connectionState,
                    isSimulator = uiState.isSimulatorMode,
                    voiceMode = uiState.voiceMode,
                    availableVoiceModes = uiState.availableVoiceModes,
                    onSelectVoiceMode = { mode -> viewModel.setVoiceMode(mode) },
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

                Spacer(modifier = Modifier.height(8.dp))

                // 1. Interactive Voice Visualizer Orb
                VoiceVisualizerOrb(
                    interactionState = uiState.interactionState,
                    userAmplitude = uiState.userAmplitude,
                    aiAmplitude = uiState.aiAmplitude,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Focused Single Subtitle / Dialogue Banner (Concise, tech-savvy)
                SingleSubtitleBanner(
                    connectionState = uiState.connectionState,
                    interactionState = uiState.interactionState,
                    messages = uiState.messages,
                    currentUserTranscript = uiState.currentUserTranscript,
                    currentAiText = uiState.currentAiText,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
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

private fun formatVoiceModeLabel(mode: String): String {
    return when (mode.trim().lowercase()) {
        "finetuned" -> "Finetuned (微调模型)"
        "omni" -> "Omni (端到端全双工)"
        "cosy" -> "Cosy (实时语音)"
        else -> mode.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

private fun formatVoiceModeChip(mode: String): String {
    val shortName = when (mode.trim().lowercase()) {
        "finetuned" -> "Finetuned"
        "omni" -> "Omni"
        "cosy" -> "Cosy"
        else -> mode.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    return "$shortName 模式"
}

@Composable
private fun TopBarHeader(
    connectionState: ConnectionState,
    isSimulator: Boolean,
    voiceMode: String,
    availableVoiceModes: List<String>,
    onSelectVoiceMode: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var modeDropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App identity & Brand avatar + Connection Status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(listOf(CyanAccent, VioletAccent)),
                        shape = RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_xiaoche_logo),
                    contentDescription = "小澈 Logo",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "小澈",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp,
                            fontSize = 19.sp
                        ),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Status Badge
                    val (statusText, statusColor) = when {
                        isSimulator -> "演示" to CyanAccent
                        connectionState is ConnectionState.Connected -> "已连通" to EmeraldSuccess
                        connectionState is ConnectionState.Connecting -> "连接中" to Color(0xFFF59E0B)
                        connectionState is ConnectionState.Error -> "离线" to CoralWarning
                        else -> "待呼叫" to TextSecondary
                    }

                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                color = statusColor
                            )
                        }
                    }
                }
                Text(
                    text = "全双工实时语音",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextSecondary.copy(alpha = 0.75f)
                )
            }
        }

        // Action items: Mode Switcher Chip + History + Settings
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Mode Switch Chip with Dropdown
            Box {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.testTag("mode_switch_chip")
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { modeDropdownExpanded = true }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatVoiceModeChip(voiceMode),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.width(1.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = CyanAccent.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = modeDropdownExpanded,
                    onDismissRequest = { modeDropdownExpanded = false }
                ) {
                    // Render dynamically from availableVoiceModes as requested, never hardcoded
                    val modes = availableVoiceModes.ifEmpty { listOf("finetuned", "omni") }
                    modes.forEach { mode ->
                        val isSelected = mode.equals(voiceMode, ignoreCase = true)
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = formatVoiceModeLabel(mode),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) CyanAccent else TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "当前模式",
                                            tint = CyanAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectVoiceMode(mode)
                                modeDropdownExpanded = false
                            },
                            modifier = Modifier.testTag("mode_option_$mode")
                        )
                    }
                }
            }

            // History button
            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .testTag("history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "历史对话",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Settings button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
