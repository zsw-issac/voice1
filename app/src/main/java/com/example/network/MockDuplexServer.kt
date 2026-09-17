package com.example.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class MockDuplexServer(
    private val listener: DuplexEventListener
) {
    private var simulationJob: Job? = null
    private var isConnected = false
    private var currentTurn = 0

    private val sampleReplies = listOf(
        "您好！全双工语音交互通道已建立。双向流式音频与实时VAD均正常工作，您可以随时说话打断我。",
        "收到您的语音输入。在全双工模式下，麦克风采集和扬声器下行是同时进行的，低延迟响应。",
        "打断测试完成。当您再次说话时，客户端会立即静音并清空播放缓冲区，向服务端下发打断信令。",
        "这是一个模拟回复。当您对接好自己的模型服务器后，可在设置中切换为远程WebSocket直连。"
    )

    fun start(coroutineScope: CoroutineScope) {
        if (isConnected) return
        isConnected = true
        listener.onConnected()
        listener.onAiStateChanged("listening")

        // Periodically report simulated low latency RTT
        coroutineScope.launch(Dispatchers.Default) {
            while (isActive && isConnected) {
                val simulatedRtt = (20..45).random().toLong()
                listener.onLatencyMeasured(simulatedRtt)
                delay(2000)
            }
        }
    }

    fun onUserSpeechDetected(coroutineScope: CoroutineScope) {
        if (!isConnected) return

        // If assistant was speaking, this is a barge-in
        simulationJob?.cancel()

        simulationJob = coroutineScope.launch(Dispatchers.Default) {
            listener.onAiStateChanged("listening")
            listener.onUserTranscript("正在倾听您的语音...", false)
            delay(500)
            listener.onUserTranscript("你好，测试全双工语音打断与流式交互。", true)

            delay(300)
            listener.onAiStateChanged("thinking")
            delay(400)

            // AI starts speaking
            listener.onAiStateChanged("speaking")
            val replyText = sampleReplies[currentTurn % sampleReplies.size]
            currentTurn++

            // Stream text tokens
            val words = replyText.chunked(2)
            var accumulated = ""
            for (word in words) {
                if (!isActive) break
                accumulated += word
                listener.onAiText(accumulated, accumulated == replyText)
                delay(80)
            }

            // Stream audio chunks (Generate 16kHz 16-bit PCM sinusoidal speech tone bursts)
            val sampleRate = 16000
            val chunkSamples = 640 // 40ms chunk = 1280 bytes
            val totalDurationMs = 2800
            val totalChunks = totalDurationMs / 40

            var phase = 0.0
            val frequencies = doubleArrayOf(350.0, 440.0, 523.25, 659.25, 440.0)

            for (chunkIndex in 0 until totalChunks) {
                if (!isActive) break

                val freq = frequencies[(chunkIndex / 15) % frequencies.size]
                val chunkBytes = ByteArray(chunkSamples * 2)

                for (i in 0 until chunkSamples) {
                    phase += 2.0 * Math.PI * freq / sampleRate
                    // Apply envelope to avoid clicking
                    val amp = 0.35 * sin(phase)
                    val sampleValue = (amp * 32767).toInt().coerceIn(-32768, 32767).toShort()
                    chunkBytes[i * 2] = (sampleValue.toInt() and 0xFF).toByte()
                    chunkBytes[i * 2 + 1] = ((sampleValue.toInt() shr 8) and 0xFF).toByte()
                }

                listener.onAiAudioReceived(chunkBytes)
                delay(40) // 40ms real-time streaming pace
            }

            if (isActive) {
                listener.onAiStateChanged("listening")
            }
        }
    }

    fun onUserInterrupt() {
        simulationJob?.cancel()
        simulationJob = null
        listener.onAiStateChanged("interrupted")
        listener.onAiText("[已打断]", true)
    }

    fun stop() {
        isConnected = false
        simulationJob?.cancel()
        simulationJob = null
        listener.onDisconnected("Simulator stopped")
    }
}
