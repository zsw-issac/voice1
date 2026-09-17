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
    fun onAiAudioReceived(pcmData: ByteArray)
    fun onUserTranscript(text: String, isFinal: Boolean)
    fun onAiText(text: String, isFinal: Boolean)
    fun onAiStateChanged(state: String)
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

                // Start periodic ping for RTT latency measurement
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
                // Direct binary PCM from server
                listener.onAiAudioReceived(data)
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
                    val clientTs = json.optLong("client_timestamp", 0L)
                    if (clientTs > 0) {
                        val rtt = (System.currentTimeMillis() - clientTs).coerceAtLeast(1)
                        _rttMs.value = rtt
                        listener.onLatencyMeasured(rtt)
                    }
                }
                DuplexProtocol.TYPE_AI_AUDIO -> {
                    val base64 = json.optString("data", "")
                    if (base64.isNotEmpty()) {
                        val pcm = DuplexProtocol.decodeBase64Audio(base64)
                        if (pcm != null && pcm.isNotEmpty()) {
                            listener.onAiAudioReceived(pcm)
                        }
                    }
                }
                DuplexProtocol.TYPE_USER_TRANSCRIPT -> {
                    val transcript = json.optString("text", "")
                    val isFinal = json.optBoolean("is_final", false)
                    listener.onUserTranscript(transcript, isFinal)
                }
                DuplexProtocol.TYPE_AI_TEXT -> {
                    val aiText = json.optString("text", "")
                    val isFinal = json.optBoolean("is_final", false)
                    listener.onAiText(aiText, isFinal)
                }
                DuplexProtocol.TYPE_AI_STATE -> {
                    val state = json.optString("state", "idle")
                    listener.onAiStateChanged(state)
                }
                DuplexProtocol.TYPE_SESSION_READY -> {
                    listener.onAiStateChanged("ready")
                }
                DuplexProtocol.TYPE_ERROR -> {
                    val msg = json.optString("message", "服务端异常")
                    listener.onError(msg)
                }
                else -> {
                    Log.d(tag, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse json: ${e.message}")
        }
    }

    /**
     * Sends an audio frame to the server (either raw binary PCM or Base64 JSON).
     */
    fun sendAudioFrame(pcmData: ByteArray) {
        val ws = webSocket ?: return
        if (_connectionState.value !is ConnectionState.Connected) return

        try {
            if (isBinaryMode) {
                ws.send(pcmData.toByteString())
                _bytesSent.value += pcmData.size
            } else {
                val json = DuplexProtocol.buildAudioChunkJson(pcmData)
                ws.send(json)
                _bytesSent.value += json.toByteArray().size
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to send audio frame: ${e.message}")
        }
    }

    /**
     * Sends user interruption notification (Barge-in).
     */
    fun sendInterrupt() {
        val ws = webSocket ?: return
        try {
            val json = DuplexProtocol.buildUserInterrupt()
            ws.send(json)
            _bytesSent.value += json.toByteArray().size
        } catch (e: Exception) {
            Log.e(tag, "Failed to send interrupt: ${e.message}")
        }
    }

    /**
     * Sends session initialization handshake.
     */
    fun sendSessionStart(sampleRate: Int, frameDurationMs: Int, autoInterrupt: Boolean) {
        val ws = webSocket ?: return
        try {
            val json = DuplexProtocol.buildSessionStart(
                sampleRate = sampleRate,
                frameDurationMs = frameDurationMs,
                autoInterrupt = autoInterrupt
            )
            ws.send(json)
            _bytesSent.value += json.toByteArray().size
        } catch (e: Exception) {
            Log.e(tag, "Failed to send session start: ${e.message}")
        }
    }

    /**
     * Sends a text message (fallback or multimodal text input).
     */
    fun sendTextMessage(text: String) {
        val ws = webSocket ?: return
        try {
            val json = DuplexProtocol.buildTextInput(text)
            ws.send(json)
            _bytesSent.value += json.toByteArray().size
        } catch (e: Exception) {
            Log.e(tag, "Failed to send text message: ${e.message}")
        }
    }

    fun disconnect() {
        pingJob?.cancel()
        pingJob = null
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.w(tag, "Error closing websocket: ${e.message}")
        }
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
