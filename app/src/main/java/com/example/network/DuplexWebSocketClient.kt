package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val url: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

interface DuplexEventListener {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onError(error: String)
    fun onServerError(code: String, message: String) {
        onError("[$code] $message")
    }
    fun onAiAudioReceived(pcmData: ByteArray, isLast: Boolean, responseId: Int)
    fun onTranscriptDelta(delta: String, responseId: Int)
    fun onTranscriptFinal(text: String, responseId: Int)
    fun onAiStateChanged(state: String)
    fun onResponseStarted(responseId: Int)
    fun onResponseInterrupted(responseId: Int)
    fun onResponseDone(responseId: Int, cancelled: Boolean)
    fun onSessionReady(sessionId: String, model: String, voiceMode: String = "finetuned", voiceModes: List<String> = listOf("finetuned", "omni"))
    fun onVoiceModeUpdated(voiceMode: String, availableModes: List<String>)
    fun onLatencyMeasured(rttMs: Long)
}

class DuplexWebSocketClient(
    private val listener: DuplexEventListener
) {
    private val tag = "DuplexWSClient"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for WebSocket
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Traffic statistics
    private val _bytesSent = MutableStateFlow(0L)
    val bytesSent: StateFlow<Long> = _bytesSent.asStateFlow()

    private val _bytesReceived = MutableStateFlow(0L)
    val bytesReceived: StateFlow<Long> = _bytesReceived.asStateFlow()

    private val _rttMs = MutableStateFlow(0L)
    val rttMs: StateFlow<Long> = _rttMs.asStateFlow()

    // Scheme A: true (binary raw PCM uplink), Scheme B: false (input_audio_buffer.append JSON)
    var isBinaryMode: Boolean = true

    fun connect(url: String, coroutineScope: CoroutineScope) {
        if (_connectionState.value is ConnectionState.Connected ||
            _connectionState.value is ConnectionState.Connecting
        ) {
            return
        }

        _connectionState.value = ConnectionState.Connecting
        _bytesSent.value = 0L
        _bytesReceived.value = 0L

        val request = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("URL格式错误: ${e.message}")
            listener.onError("URL格式错误: ${e.message}")
            return
        }

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(tag, "WebSocket connected: $url")
                _connectionState.value = ConnectionState.Connected(url)
                listener.onConnected()

                // Start periodic ping for RTT latency measurement using {"type":"ping","ts":...}
                pingJob = coroutineScope.launch(Dispatchers.IO) {
                    while (isActive && _connectionState.value is ConnectionState.Connected) {
                        try {
                            val pingJson = DuplexProtocol.buildPing()
                            ws.send(pingJson)
                        } catch (e: Exception) {
                            Log.w(tag, "Ping error: ${e.message}")
                        }
                        delay(3000)
                    }
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                _bytesReceived.value += text.toByteArray().size
                handleIncomingJson(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                _bytesReceived.value += data.size

                // Scheme A Downlink binary frame
                // Byte 0: Bit 7 = isLast, Bit 0-6 = response_id
                // Byte 1..N: PCM 16bit 24000Hz audio
                val parsed = DuplexProtocol.parseDownlinkBinaryFrame(data)
                if (parsed != null) {
                    listener.onAiAudioReceived(
                        pcmData = parsed.pcmAudio,
                        isLast = parsed.isLast,
                        responseId = parsed.responseId
                    )
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing: $code $reason")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed: $code $reason")
                _connectionState.value = ConnectionState.Disconnected
                pingJob?.cancel()
                listener.onDisconnected(reason)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message}", t)
                val errorMsg = t.message ?: "网络连接异常"
                _connectionState.value = ConnectionState.Error(errorMsg)
                pingJob?.cancel()
                listener.onError(errorMsg)
            }
        })
    }

    private fun handleIncomingJson(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "")

            when (type) {
                DuplexProtocol.TYPE_PONG -> {
                    val ts = json.optLong("ts", 0L)
                    if (ts > 0) {
                        val rtt = (System.currentTimeMillis() - ts).coerceAtLeast(1)
                        _rttMs.value = rtt
                        listener.onLatencyMeasured(rtt)
                    }
                }

                DuplexProtocol.TYPE_SESSION_READY -> {
                    val sessionId = json.optString("session_id", "")
                    val model = json.optString("model", "")
                    val voiceMode = json.optString("voice_mode", "finetuned")
                    val modesArray = json.optJSONArray("voice_modes")
                    val voiceModes = mutableListOf<String>()
                    if (modesArray != null) {
                        for (i in 0 until modesArray.length()) {
                            val modeItem = modesArray.optString(i, "")
                            if (modeItem.isNotEmpty()) {
                                voiceModes.add(modeItem)
                            }
                        }
                    } else if (voiceMode.isNotEmpty()) {
                        voiceModes.add(voiceMode)
                    }
                    Log.d(tag, "session.ready: id=$sessionId model=$model voice_mode=$voiceMode voice_modes=$voiceModes")
                    listener.onSessionReady(sessionId, model, voiceMode, voiceModes)
                }

                DuplexProtocol.TYPE_VOICE_MODE -> {
                    val mode = json.optString("value", "")
                    val availableArray = json.optJSONArray("available")
                    val available = mutableListOf<String>()
                    if (availableArray != null) {
                        for (i in 0 until availableArray.length()) {
                            val modeItem = availableArray.optString(i, "")
                            if (modeItem.isNotEmpty()) {
                                available.add(modeItem)
                            }
                        }
                    }
                    Log.d(tag, "voice_mode received: value=$mode available=$available")
                    listener.onVoiceModeUpdated(mode, available)
                }

                DuplexProtocol.TYPE_STATE -> {
                    val stateValue = json.optString("value", "listening")
                    listener.onAiStateChanged(stateValue)
                }

                DuplexProtocol.TYPE_RESPONSE_START -> {
                    val responseId = json.optInt("response_id", 0)
                    listener.onResponseStarted(responseId)
                }

                DuplexProtocol.TYPE_TRANSCRIPT -> {
                    val isFinal = json.optBoolean("final", false)
                    val responseId = json.optInt("response_id", 0)
                    if (isFinal) {
                        val fullText = json.optString("text", "")
                        listener.onTranscriptFinal(fullText, responseId)
                    } else {
                        val delta = json.optString("delta", "")
                        if (delta.isNotEmpty()) {
                            listener.onTranscriptDelta(delta, responseId)
                        }
                    }
                }

                DuplexProtocol.TYPE_RESPONSE_AUDIO_DELTA -> {
                    // Scheme B Downlink
                    val base64 = json.optString("delta", "")
                    val responseId = json.optInt("response_id", 0)
                    val isLast = json.optBoolean("last", false)
                    if (base64.isNotEmpty()) {
                        val pcm = DuplexProtocol.decodeBase64Audio(base64)
                        if (pcm != null && pcm.isNotEmpty()) {
                            listener.onAiAudioReceived(pcm, isLast, responseId)
                        } else if (isLast) {
                            listener.onAiAudioReceived(ByteArray(0), true, responseId)
                        }
                    } else if (isLast) {
                        listener.onAiAudioReceived(ByteArray(0), true, responseId)
                    }
                }

                DuplexProtocol.TYPE_RESPONSE_INTERRUPTED -> {
                    val responseId = json.optInt("response_id", 0)
                    listener.onResponseInterrupted(responseId)
                }

                DuplexProtocol.TYPE_RESPONSE_DONE -> {
                    val responseId = json.optInt("response_id", 0)
                    val cancelled = json.optBoolean("cancelled", false)
                    listener.onResponseDone(responseId, cancelled)
                }

                DuplexProtocol.TYPE_ERROR -> {
                    val code = json.optString("code", "")
                    val msg = json.optString("message", "服务端错误: $code")
                    Log.w(tag, "Server error: code=$code msg=$msg")
                    listener.onServerError(code, msg)
                }

                else -> {
                    Log.d(tag, "Ignored or unhandled message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse incoming json: ${e.message}")
        }
    }

    /**
     * Sends an audio frame to the server.
     * Scheme A: Raw 16-bit PCM binary frame (1280 bytes for 40ms @ 16kHz).
     * Scheme B: {"type":"input_audio_buffer.append","audio":"<base64>"}
     */
    fun sendAudioFrame(pcmData: ByteArray) {
        val ws = webSocket ?: return
        if (_connectionState.value !is ConnectionState.Connected) return

        try {
            if (isBinaryMode) {
                // Scheme A: Raw PCM binary frame
                ws.send(pcmData.toByteString())
                _bytesSent.value += pcmData.size
            } else {
                // Scheme B: JSON format
                val json = DuplexProtocol.buildAudioBufferAppend(pcmData)
                ws.send(json)
                _bytesSent.value += json.toByteArray().size
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to send audio frame: ${e.message}")
        }
    }

    /**
     * Sends interruption notification (Barge-in).
     * Server expects {"type":"response.cancel"}.
     */
    fun sendInterrupt() {
        val ws = webSocket ?: return
        try {
            val json = DuplexProtocol.buildInterrupt()
            ws.send(json)
            _bytesSent.value += json.toByteArray().size
        } catch (e: Exception) {
            Log.e(tag, "Failed to send interrupt: ${e.message}")
        }
    }

    /**
     * Sends voice mode switch request:
     * {"type":"session.set_voice_mode","value":"omni" | "cosy"}
     */
    fun sendVoiceMode(mode: String) {
        val ws = webSocket ?: return
        try {
            val json = DuplexProtocol.buildSetVoiceMode(mode)
            ws.send(json)
            _bytesSent.value += json.toByteArray().size
            Log.d(tag, "Sent session.set_voice_mode: $mode")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send voice mode: ${e.message}")
        }
    }

    /**
     * Closes the session politely with session.finish before closing websocket.
     */
    fun disconnect() {
        pingJob?.cancel()
        pingJob = null
        try {
            webSocket?.send(DuplexProtocol.buildSessionFinish())
        } catch (e: Exception) {
            // Ignore
        }
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.w(tag, "Error closing websocket: ${e.message}")
        }
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
