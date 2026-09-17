package com.example.network

import org.json.JSONObject
import android.util.Base64

/**
 * Protocol specification and message parser for full-duplex voice communication.
 */
object DuplexProtocol {

    // Message Types (Client -> Server)
    const val TYPE_SESSION_START = "session_start"
    const val TYPE_AUDIO_CHUNK = "audio_chunk"
    const val TYPE_USER_INTERRUPT = "user_interrupt"
    const val TYPE_TEXT_INPUT = "text_input"
    const val TYPE_PING = "ping"
    const val TYPE_SESSION_END = "session_end"

    // Message Types (Server -> Client)
    const val TYPE_SESSION_READY = "session_ready"
    const val TYPE_USER_TRANSCRIPT = "user_transcript"
    const val TYPE_AI_TEXT = "ai_text"
    const val TYPE_AI_AUDIO = "ai_audio"
    const val TYPE_AI_STATE = "ai_state"
    const val TYPE_PONG = "pong"
    const val TYPE_ERROR = "error"

    /**
     * Creates a session_start JSON message.
     */
    fun buildSessionStart(
        sampleRate: Int,
        channels: Int = 1,
        format: String = "pcm16",
        frameDurationMs: Int = 40,
        autoInterrupt: Boolean = true,
        systemPrompt: String = "You are a helpful, conversational voice assistant."
    ): String {
        return JSONObject().apply {
            put("type", TYPE_SESSION_START)
            put("sample_rate", sampleRate)
            put("channels", channels)
            put("format", format)
            put("frame_duration_ms", frameDurationMs)
            put("auto_interrupt", autoInterrupt)
            put("system_prompt", systemPrompt)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    /**
     * Creates an audio chunk JSON message with Base64 payload (for JSON mode).
     */
    fun buildAudioChunkJson(pcmData: ByteArray): String {
        val base64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        return JSONObject().apply {
            put("type", TYPE_AUDIO_CHUNK)
            put("data", base64)
            put("size", pcmData.size)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    /**
     * Creates a user interrupt notification JSON message (Barge-in).
     */
    fun buildUserInterrupt(): String {
        return JSONObject().apply {
            put("type", TYPE_USER_INTERRUPT)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    /**
     * Creates a fallback text message.
     */
    fun buildTextInput(text: String): String {
        return JSONObject().apply {
            put("type", TYPE_TEXT_INPUT)
            put("text", text)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    /**
     * Creates a ping message for RTT latency measurement.
     */
    fun buildPing(clientTimestamp: Long = System.currentTimeMillis()): String {
        return JSONObject().apply {
            put("type", TYPE_PING)
            put("client_timestamp", clientTimestamp)
        }.toString()
    }

    /**
     * Decodes Base64 audio from an incoming JSON object.
     */
    fun decodeBase64Audio(base64String: String): ByteArray? {
        return try {
            Base64.decode(base64String, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Server specification docs shown to the user in the app.
     */
    const val SPECIFICATION_DOCS_MARKDOWN = """
# 全双工语音交互 WebSocket 对接协议规范

本客户端作为全双工语音交互的终端，提供实时音频采集流发送、实时下行流播放与打断 (Barge-in) 机制。

## 1. 连接方式
- **协议**: WebSocket (`ws://` 或 `wss://`)
- **默认地址**: `ws://<服务器IP>:<端口>/ws/audio`
- **传输模式**:
  - **二进制模式 (Binary Mode, 推荐)**: 音频上行/下行直接为 16-bit PCM 二进制帧，控制信令为 JSON 文本。延迟最低。
  - **JSON Base64 模式**: 音频以 Base64 包装在 JSON 消息中。调试方便。

## 2. 音频编码参数
- **采样率**: 16000 Hz 或 24000 Hz (可在设置中选择)
- **声道**: 单声道 (Mono)
- **量化**: 16-bit Signed Linear PCM (小端序 Little-Endian)
- **帧长**: 20ms / 40ms / 100ms (默认 40ms = 640 字节 @ 16kHz)

## 3. 客户端发送信令 (Client -> Server)
- **会话握手**:
  ```json
  {
    "type": "session_start",
    "sample_rate": 16000,
    "channels": 1,
    "format": "pcm16",
    "auto_interrupt": true
  }
  ```
- **音频流**:
  - 二进制模式: 直接发送原始 PCM ByteArray 帧 (Opcode 0x2)
  - JSON模式:
    ```json
    { "type": "audio_chunk", "data": "<base64_pcm>", "timestamp": 1720000000000 }
    ```
- **打断信令 (Barge-in)**:
  当用户开始说话且检测到打断时发送，服务端应立即停止当前大模型生成和 TTS 推流：
  ```json
  { "type": "user_interrupt", "timestamp": 1720000000000 }
  ```
- **心跳/延迟测算**:
  ```json
  { "type": "ping", "client_timestamp": 1720000000000 }
  ```

## 4. 服务端下发信令 (Server -> Client)
- **连接就绪**:
  ```json
  { "type": "session_ready", "session_id": "sess_123" }
  ```
- **用户识别转写 (ASR)**:
  ```json
  { "type": "user_transcript", "text": "你好", "is_final": true }
  ```
- **模型回复文本 (LLM Token)**:
  ```json
  { "type": "ai_text", "text": "你好！有什么我可以帮你的？", "is_final": false }
  ```
- **模型语音下发 (TTS Audio)**:
  - 二进制模式: 直接下发 PCM 16bit 二进制流 (Opcode 0x2)
  - JSON模式:
    ```json
    { "type": "ai_audio", "data": "<base64_pcm>" }
    ```
- **状态同步**:
  ```json
  { "type": "ai_state", "state": "speaking" } // idle / listening / thinking / speaking / interrupted
  ```
- **心跳响应**:
  ```json
  { "type": "pong", "client_timestamp": 1720000000000, "server_timestamp": 1720000000010 }
  ```
"""
}
