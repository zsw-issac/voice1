package com.example.network

import org.json.JSONObject
import android.util.Base64

/**
 * Protocol specification matching server/app.py + server/protocol.py.
 */
object DuplexProtocol {

    // Default Server Endpoints
    const val DEFAULT_SERVER_URL = "wss://voice.zswen.online/ws/duplex"
    const val ALIAS_SERVER_URL = "wss://voice.zswen.online/v1/realtime"

    // Client -> Server Types
    const val TYPE_INPUT_AUDIO_APPEND = "input_audio_buffer.append"
    const val TYPE_RESPONSE_CANCEL = "response.cancel"
    const val TYPE_BARGE_IN = "barge_in"
    const val TYPE_SESSION_START = "session.start"
    const val TYPE_SESSION_UPDATE = "session.update"
    const val TYPE_SESSION_FINISH = "session.finish"
    const val TYPE_SESSION_SET_VOICE_MODE = "session.set_voice_mode"
    const val TYPE_PING = "ping"

    // Server -> Client Types
    const val TYPE_SESSION_READY = "session.ready"
    const val TYPE_VOICE_MODE = "voice_mode"
    const val TYPE_STATE = "state"
    const val TYPE_RESPONSE_START = "response.start"
    const val TYPE_TRANSCRIPT = "transcript"
    const val TYPE_RESPONSE_AUDIO_DELTA = "response.audio.delta"
    const val TYPE_RESPONSE_INTERRUPTED = "response.interrupted"
    const val TYPE_RESPONSE_DONE = "response.done"
    const val TYPE_PONG = "pong"
    const val TYPE_ERROR = "error"

    /**
     * Creates Scheme B audio append message:
     * {"type":"input_audio_buffer.append","audio":"<base64>"}
     */
    fun buildAudioBufferAppend(pcmData: ByteArray): String {
        val base64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        return JSONObject().apply {
            put("type", TYPE_INPUT_AUDIO_APPEND)
            put("audio", base64)
        }.toString()
    }

    /**
     * Creates interrupt message recognized by server:
     * {"type":"response.cancel"}
     */
    fun buildInterrupt(): String {
        return JSONObject().apply {
            put("type", TYPE_RESPONSE_CANCEL)
        }.toString()
    }

    /**
     * Creates ping message. Server only inspects 'ts' field and echoes it back in pong.
     */
    fun buildPing(ts: Long = System.currentTimeMillis()): String {
        return JSONObject().apply {
            put("type", TYPE_PING)
            put("ts", ts)
        }.toString()
    }

    /**
     * Creates session.finish message to end session gracefully.
     */
    fun buildSessionFinish(): String {
        return JSONObject().apply {
            put("type", TYPE_SESSION_FINISH)
        }.toString()
    }

    /**
     * Creates session.set_voice_mode message:
     * {"type":"session.set_voice_mode","value":"omni" | "cosy"}
     */
    fun buildSetVoiceMode(mode: String): String {
        return JSONObject().apply {
            put("type", TYPE_SESSION_SET_VOICE_MODE)
            put("value", mode)
        }.toString()
    }

    /**
     * Decodes Base64 audio from Scheme B (response.audio.delta).
     */
    fun decodeBase64Audio(base64String: String): ByteArray? {
        return try {
            Base64.decode(base64String, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parsed Downlink Binary Frame (Scheme A).
     * Format:
     * Offset 0: 1-byte header:
     *   - Bit 7 (0x80): isLast (1 = audio finished for this response)
     *   - Bit 0-6 (0x7F): responseId (0-127)
     * Offset 1..N: PCM16 Little-Endian 24000Hz mono audio data (up to 1920 bytes)
     * NOTE: LAST frame payload length may be 0 (header only).
     */
    data class DownlinkBinaryFrame(
        val isLast: Boolean,
        val responseId: Int,
        val pcmAudio: ByteArray
    )

    fun parseDownlinkBinaryFrame(rawBytes: ByteArray): DownlinkBinaryFrame? {
        if (rawBytes.isEmpty()) return null
        val header = rawBytes[0].toInt() and 0xFF
        val isLast = (header and 0x80) != 0
        val responseId = header and 0x7F
        val audioBytes = if (rawBytes.size > 1) {
            rawBytes.copyOfRange(1, rawBytes.size)
        } else {
            ByteArray(0)
        }
        return DownlinkBinaryFrame(
            isLast = isLast,
            responseId = responseId,
            pcmAudio = audioBytes
        )
    }

    /**
     * Server specification docs shown to the user in the app.
     */
    const val SPECIFICATION_DOCS_MARKDOWN = """
# 全双工语音服务 · 服务端对接与行为说明

本客户端已全面适配实际服务端协议规范（对应 `server/app.py` + `server/protocol.py` + `server/tts_service.py`）。

## 1. 连接地址
- **监听地址**: `ws://<host>:8080/ws/duplex`
- **等价别名**: `ws://<host>:8080/v1/realtime`
- **测试页**: 同端口 `GET /test.html`

## 2. 握手与首包
- 客户端连接后无需发送初始化文本，服务端连接即主动下发：
  `{"type":"session.ready","session_id":"...","uplink":{"sample_rate":16000},"downlink":{"sample_rate":24000}}`

## 3. 音频参数与分频
- **上行 (Mic -> Server)**: 16000 Hz, 单声道, 16-bit 小端 PCM, 40ms = **1280 字节**
- **下行 (Server -> Spk)**: 24000 Hz, 单声道, 16-bit 小端 PCM, 40ms = **1920 字节 + 1 字节帧头**

## 4. 上行方案判定（按首帧自动固定）
- **方案 A (推荐)**: 客户端首帧为二进制裸 PCM16，此后音频全走二进制，文本走 JSON。
- **方案 B**: 首帧发送 `input_audio_buffer.append` JSON 文本，包含 Base64 编码音频。

## 5. 下行二进制帧解析 (方案 A)
- **字节 0**: 1 字节控制头
  - bit 7 (0x80): 1 = 本轮回答音频结束 (LAST 帧可能载荷为 0 字节)
  - bit 0–6 (0x7F): `response_id` (0~127)
- **字节 1~末尾**: 24000Hz 16-bit PCM 音频流

## 6. 即时打断 (Barge-in)
- 客户端发送 `{"type":"response.cancel"}`
- 客户端本地瞬时清空 AudioTrack 播放队列与缓冲
- 服务端检测到打断后立即终止 LLM 生成与 TTS 推流，若有活跃回答则下发 `response.interrupted`
"""
}
