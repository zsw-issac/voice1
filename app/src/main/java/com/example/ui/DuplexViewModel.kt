package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCaptureManager
import com.example.audio.AudioConfig
import com.example.audio.AudioPlaybackManager
import com.example.data.ConversationEntity
import com.example.data.DuplexDatabase
import com.example.data.DuplexRepository
import com.example.data.MessageEntity
import com.example.network.ConnectionState
import com.example.network.DuplexEventListener
import com.example.network.DuplexProtocol
import com.example.network.DuplexWebSocketClient
import com.example.network.MockDuplexServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InteractionState(val label: String) {
    IDLE("待呼叫"),
    LISTENING("正在聆听"),
    USER_SPEAKING("您正在讲话"),
    THINKING("小澈思考中"),
    AI_SPEAKING("小澈正在回答"),
    INTERRUPTED("已打断小澈")
}

data class DuplexUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val interactionState: InteractionState = InteractionState.IDLE,
    val serverUrl: String = DuplexProtocol.DEFAULT_SERVER_URL,
    val isSimulatorMode: Boolean = false,
    val uplinkSampleRate: Int = 16000,
    val downlinkSampleRate: Int = 24000,
    val frameDurationMs: Int = 40,
    val isBinaryMode: Boolean = true, // Scheme A vs Scheme B
    val autoInterrupt: Boolean = true,
    val vadThreshold: Float = 0.08f,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val userAmplitude: Float = 0f,
    val aiAmplitude: Float = 0f,
    val currentUserTranscript: String = "",
    val currentAiText: String = "",
    val currentResponseId: Int = 0,
    val serverModel: String = "",
    val sessionId: String = "",
    val rttMs: Long = 0,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val currentConversationId: Long = 0,
    val messages: List<MessageEntity> = emptyList(),
    val conversations: List<ConversationEntity> = emptyList(),
    val voiceMode: String = "finetuned",
    val availableVoiceModes: List<String> = listOf("finetuned", "omni"),
    val errorMessage: String? = null
) {
    // Helper for UI sample rate display
    val sampleRate: Int
        get() = uplinkSampleRate
}

class DuplexViewModel(application: Application) : AndroidViewModel(application), DuplexEventListener {
    private val tag = "DuplexViewModel"

    private val repository: DuplexRepository
    private var audioConfig: AudioConfig
    private var audioCapture: AudioCaptureManager
    private var audioPlayback: AudioPlaybackManager
    private var wsClient: DuplexWebSocketClient
    private var mockServer: MockDuplexServer

    private val _uiState = MutableStateFlow(DuplexUiState())
    val uiState: StateFlow<DuplexUiState> = _uiState.asStateFlow()

    private var messageObserveJob: Job? = null

    init {
        val db = DuplexDatabase.getDatabase(application)
        repository = DuplexRepository(db.conversationDao())

        // 16kHz Uplink, 24kHz Downlink
        audioConfig = AudioConfig(
            uplinkSampleRate = 16000,
            downlinkSampleRate = 24000,
            frameDurationMs = 40,
            vadEnergyThreshold = 0.08f,
            autoInterruptOnSpeech = true
        )

        audioPlayback = AudioPlaybackManager(application, audioConfig)

        audioCapture = AudioCaptureManager(
            context = application,
            config = audioConfig,
            onAudioFrame = { pcmData, rms, isSpeech ->
                _uiState.update { it.copy(userAmplitude = rms) }
                if (_uiState.value.isSimulatorMode) {
                    if (isSpeech) {
                        mockServer.onUserSpeechDetected(viewModelScope)
                    }
                } else {
                    wsClient.sendAudioFrame(pcmData)
                }
            },
            onBargeInDetected = {
                triggerBargeIn()
            }
        )

        wsClient = DuplexWebSocketClient(this)
        mockServer = MockDuplexServer(this)

        // Observe stored conversations
        viewModelScope.launch {
            repository.allConversations.collect { list ->
                _uiState.update { it.copy(conversations = list) }
            }
        }

        // Observe amplitude from playback manager
        viewModelScope.launch {
            audioPlayback.currentRms.collect { rms ->
                _uiState.update { it.copy(aiAmplitude = rms) }
            }
        }

        // Observe network stats
        viewModelScope.launch {
            wsClient.bytesSent.collect { sent ->
                _uiState.update { it.copy(bytesSent = sent) }
            }
        }
        viewModelScope.launch {
            wsClient.bytesReceived.collect { rcv ->
                _uiState.update { it.copy(bytesReceived = rcv) }
            }
        }
    }

    /**
     * Start/stop the full duplex session.
     */
    fun toggleConnection() {
        val currentState = _uiState.value.connectionState
        if (currentState is ConnectionState.Connected || currentState is ConnectionState.Connecting) {
            disconnect()
        } else {
            connect()
        }
    }

    private fun connect() {
        viewModelScope.launch {
            // Start audio playback engine (24000Hz)
            audioPlayback.start(viewModelScope)

            // Start audio recording (16000Hz)
            val recordStarted = audioCapture.startRecording(viewModelScope)
            if (!recordStarted && !audioCapture.hasRecordPermission()) {
                _uiState.update {
                    it.copy(errorMessage = "未授予麦克风权限，无法开启实时语音采集")
                }
                return@launch
            }

            // Create a conversation record in database
            val sessionTitle = "语音会话 " + android.text.format.DateFormat.format("MM-dd HH:mm", System.currentTimeMillis())
            val convId = repository.createConversation(sessionTitle)
            _uiState.update {
                it.copy(
                    currentConversationId = convId,
                    currentUserTranscript = "",
                    currentAiText = "",
                    interactionState = InteractionState.LISTENING
                )
            }

            observeMessages(convId)

            if (_uiState.value.isSimulatorMode) {
                mockServer.start(viewModelScope)
            } else {
                wsClient.isBinaryMode = _uiState.value.isBinaryMode
                wsClient.connect(_uiState.value.serverUrl, viewModelScope)
            }
        }
    }

    private fun observeMessages(convId: Long) {
        messageObserveJob?.cancel()
        messageObserveJob = viewModelScope.launch {
            repository.getMessagesForConversation(convId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun disconnect() {
        audioCapture.stopRecording()
        audioPlayback.stop()

        if (_uiState.value.isSimulatorMode) {
            mockServer.stop()
        } else {
            wsClient.disconnect()
        }

        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Disconnected,
                interactionState = InteractionState.IDLE,
                userAmplitude = 0f,
                aiAmplitude = 0f
            )
        }
    }

    /**
     * Full-duplex Barge-in (打断机制):
     * Can be called automatically by VAD or manually via UI "打断" button.
     */
    fun triggerBargeIn() {
        // 1. Immediately mute and flush the local AudioTrack playback queue
        audioPlayback.interrupt()
        audioCapture.isAiSpeaking = false

        // 2. Notify remote server (sends {"type":"response.cancel"})
        if (_uiState.value.isSimulatorMode) {
            mockServer.onUserInterrupt()
        } else {
            wsClient.sendInterrupt()
        }

        // 3. Update interaction state to INTERRUPTED, save transcript if any
        val currentAi = _uiState.value.currentAiText
        val convId = _uiState.value.currentConversationId
        if (currentAi.isNotEmpty() && convId > 0) {
            viewModelScope.launch {
                repository.addMessage(
                    conversationId = convId,
                    role = "assistant",
                    content = "$currentAi (已打断)",
                    wasInterrupted = true
                )
            }
        }

        _uiState.update {
            it.copy(
                interactionState = InteractionState.INTERRUPTED,
                currentAiText = "",
                aiAmplitude = 0f
            )
        }
    }

    fun toggleMicMute() {
        val newMute = !_uiState.value.isMicMuted
        audioCapture.setMuted(newMute)
        _uiState.update { it.copy(isMicMuted = newMute) }
    }

    fun toggleSpeaker() {
        val newSpeaker = audioPlayback.toggleSpeakerphone()
        _uiState.update { it.copy(isSpeakerOn = newSpeaker) }
    }

    /**
     * Switch voice mode (e.g. "finetuned" vs "omni").
     * Sends session.set_voice_mode to server and updates local state.
     * Can be sent at any time (including while AI is speaking).
     */
    fun setVoiceMode(mode: String) {
        Log.d(tag, "Switching voice mode to: $mode")
        _uiState.update { it.copy(voiceMode = mode) }
        if (!_uiState.value.isSimulatorMode) {
            wsClient.sendVoiceMode(mode)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        val convId = _uiState.value.currentConversationId
        viewModelScope.launch {
            if (convId > 0) {
                repository.addMessage(convId, "user", text)
            }
            if (_uiState.value.isSimulatorMode) {
                mockServer.onUserSpeechDetected(viewModelScope)
            }
        }
    }

    fun updateSettings(
        serverUrl: String,
        isSimulatorMode: Boolean,
        sampleRate: Int,
        frameDurationMs: Int,
        isBinaryMode: Boolean,
        autoInterrupt: Boolean,
        vadThreshold: Float
    ) {
        _uiState.update {
            it.copy(
                serverUrl = serverUrl,
                isSimulatorMode = isSimulatorMode,
                uplinkSampleRate = sampleRate,
                frameDurationMs = frameDurationMs,
                isBinaryMode = isBinaryMode,
                autoInterrupt = autoInterrupt,
                vadThreshold = vadThreshold
            )
        }

        // Recreate audio config
        audioConfig = AudioConfig(
            uplinkSampleRate = sampleRate,
            downlinkSampleRate = 24000,
            frameDurationMs = frameDurationMs,
            vadEnergyThreshold = vadThreshold,
            autoInterruptOnSpeech = autoInterrupt
        )
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
            _uiState.update { it.copy(messages = emptyList()) }
        }
    }

    // --- DuplexEventListener Implementations ---

    override fun onConnected() {
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Connected(it.serverUrl),
                interactionState = InteractionState.LISTENING,
                errorMessage = null
            )
        }
    }

    override fun onDisconnected(reason: String) {
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Disconnected,
                interactionState = InteractionState.IDLE
            )
        }
    }

    override fun onError(error: String) {
        Log.e(tag, "Duplex error: $error")
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Error(error),
                interactionState = InteractionState.IDLE,
                errorMessage = error
            )
        }
    }

    override fun onServerError(code: String, message: String) {
        Log.w(tag, "Duplex server error: code=$code message=$message")
        _uiState.update {
            it.copy(
                errorMessage = if (message.isNotBlank()) message else "服务端错误: $code"
            )
        }
    }

    override fun onSessionReady(sessionId: String, model: String, voiceMode: String, voiceModes: List<String>) {
        Log.d(tag, "Session ready: id=$sessionId model=$model voiceMode=$voiceMode modes=$voiceModes")
        _uiState.update {
            it.copy(
                sessionId = sessionId,
                serverModel = model,
                voiceMode = voiceMode.ifEmpty { it.voiceMode },
                availableVoiceModes = if (voiceModes.isNotEmpty()) voiceModes else it.availableVoiceModes,
                interactionState = InteractionState.LISTENING
            )
        }
    }

    override fun onVoiceModeUpdated(voiceMode: String, availableModes: List<String>) {
        Log.d(tag, "Voice mode updated: $voiceMode available=$availableModes")
        _uiState.update {
            it.copy(
                voiceMode = voiceMode.ifEmpty { it.voiceMode },
                availableVoiceModes = if (availableModes.isNotEmpty()) availableModes else it.availableVoiceModes
            )
        }
    }

    override fun onResponseStarted(responseId: Int) {
        _uiState.update {
            it.copy(
                currentResponseId = responseId,
                currentAiText = "",
                interactionState = InteractionState.AI_SPEAKING
            )
        }
    }

    override fun onTranscriptDelta(delta: String, responseId: Int) {
        _uiState.update {
            it.copy(
                currentAiText = it.currentAiText + delta,
                interactionState = InteractionState.AI_SPEAKING
            )
        }
    }

    override fun onTranscriptFinal(text: String, responseId: Int) {
        // Clear currentAiText to prevent duplicate rendering when message is saved to DB
        _uiState.update {
            it.copy(
                currentAiText = ""
            )
        }
        val convId = _uiState.value.currentConversationId
        if (convId > 0 && text.isNotBlank()) {
            viewModelScope.launch {
                repository.addMessage(convId, "assistant", text)
            }
        }
    }

    override fun onAiAudioReceived(pcmData: ByteArray, isLast: Boolean, responseId: Int) {
        if (pcmData.isNotEmpty()) {
            audioCapture.isAiSpeaking = true
            audioPlayback.enqueueAudioChunk(pcmData)
            _uiState.update {
                it.copy(interactionState = InteractionState.AI_SPEAKING)
            }
        }
    }

    override fun onResponseInterrupted(responseId: Int) {
        audioPlayback.interrupt()
        audioCapture.isAiSpeaking = false
        _uiState.update {
            it.copy(interactionState = InteractionState.INTERRUPTED)
        }
    }

    override fun onResponseDone(responseId: Int, cancelled: Boolean) {
        if (!cancelled) {
            audioCapture.isAiSpeaking = false
            _uiState.update {
                it.copy(interactionState = InteractionState.LISTENING)
            }
        }
    }

    override fun onAiStateChanged(state: String) {
        val newState = when (state.lowercase()) {
            "listening", "ready" -> {
                audioCapture.isAiSpeaking = false
                InteractionState.LISTENING
            }
            "thinking" -> InteractionState.THINKING
            "speaking" -> {
                audioCapture.isAiSpeaking = true
                InteractionState.AI_SPEAKING
            }
            "interrupted" -> {
                audioCapture.isAiSpeaking = false
                InteractionState.INTERRUPTED
            }
            else -> InteractionState.LISTENING
        }
        _uiState.update { it.copy(interactionState = newState) }
    }

    override fun onLatencyMeasured(rttMs: Long) {
        _uiState.update { it.copy(rttMs = rttMs) }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
