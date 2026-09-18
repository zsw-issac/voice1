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
    private var currentResponseId = 0

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
        listener.onSessionReady("mock_sess_01", "Qwen2.5-Omni-7B (Simulator)", "finetuned", listOf("finetuned", "omni"))
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

        // If assistant was speaking, cancel previous
        simulationJob?.cancel()

        simulationJob = coroutineScope.launch(Dispatchers.Default) {
            val respId = ++currentResponseId

            listener.onAiStateChanged("listening")
            delay(400)
            listener.onAiStateChanged("thinking")
            delay(400)

            listener.onResponseStarted(respId)
            listener.onAiStateChanged("speaking")

            val replyText = sampleReplies[respId % sampleReplies.size]

            // Stream transcript deltas
            val words = replyText.chunked(2)
            var accumulated = ""
            for (word in words) {
                if (!isActive) break
                accumulated += word
                listener.onTranscriptDelta(word, respId)
                delay(70)
            }
            if (isActive) {
                listener.onTranscriptFinal(replyText, respId)
            }

            // Stream audio chunks (Generate 24kHz 16-bit PCM for Downlink)
            val sampleRate = 24000
            val chunkSamples = 960 // 40ms @ 24kHz = 960 samples = 1920 bytes
            val totalDurationMs = 2800
            val totalChunks = totalDurationMs / 40

            var phase = 0.0
            val frequencies = doubleArrayOf(350.0, 440.0, 523.25, 659.25, 440.0)

            for (chunkIndex in 0 until totalChunks) {
                if (!isActive) break

                val isLast = (chunkIndex == totalChunks - 1)
                val freq = frequencies[(chunkIndex / 15) % frequencies.size]
                val chunkBytes = ByteArray(chunkSamples * 2)

                for (i in 0 until chunkSamples) {
                    phase += 2.0 * Math.PI * freq / sampleRate
                    val amp = 0.32 * sin(phase)
                    val sampleValue = (amp * 32767).toInt().coerceIn(-32768, 32767).toShort()
                    chunkBytes[i * 2] = (sampleValue.toInt() and 0xFF).toByte()
                    chunkBytes[i * 2 + 1] = ((sampleValue.toInt() shr 8) and 0xFF).toByte()
                }

                listener.onAiAudioReceived(chunkBytes, isLast, respId)
                delay(40) // 40ms real-time streaming pace
            }

            if (isActive) {
                listener.onResponseDone(respId, false)
                listener.onAiStateChanged("listening")
            }
        }
    }

    fun onUserInterrupt() {
        val respId = currentResponseId
        simulationJob?.cancel()
        simulationJob = null
        listener.onResponseInterrupted(respId)
        listener.onAiStateChanged("listening")
    }

    fun stop() {
        isConnected = false
        simulationJob?.cancel()
        simulationJob = null
        listener.onDisconnected("Simulator stopped")
    }
}
