package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

class AudioPlaybackManager(
    private val context: Context,
    private val config: AudioConfig
) {
    private val tag = "AudioPlaybackManager"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()

    private val _isAiSpeaking = MutableStateFlow(false)
    val isAiSpeaking: StateFlow<Boolean> = _isAiSpeaking.asStateFlow()

    private val _currentRms = MutableStateFlow(0f)
    val currentRms: StateFlow<Float> = _currentRms.asStateFlow()

    private val _isSpeakerphoneOn = MutableStateFlow(true)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    private var bufferSize: Int = 0

    fun start(coroutineScope: CoroutineScope): Boolean {
        if (audioTrack != null) return true

        val minBufferSize = AudioTrack.getMinBufferSize(
            config.sampleRate,
            config.channelConfigOut,
            config.audioFormat
        )
        if (minBufferSize <= 0) {
            Log.e(tag, "Invalid AudioTrack buffer size")
            return false
        }

        bufferSize = maxOf(minBufferSize * 2, config.bytesPerFrame * 4)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(config.sampleRate)
            .setChannelMask(config.channelConfigOut)
            .setEncoding(config.audioFormat)
            .build()

        try {
            audioTrack = AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to create AudioTrack: ${e.message}")
            return false
        }

        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(tag, "AudioTrack not initialized")
            audioTrack?.release()
            audioTrack = null
            return false
        }

        try {
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(tag, "Failed to play AudioTrack: ${e.message}")
        }

        applySpeakerphoneMode(_isSpeakerphoneOn.value)

        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                val chunk = audioQueue.poll()
                if (chunk != null && chunk.isNotEmpty()) {
                    _isAiSpeaking.value = true
                    val rms = calculateNormalizedRms(chunk)
                    _currentRms.value = rms

                    val track = audioTrack
                    if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        var offset = 0
                        while (offset < chunk.size && isActive) {
                            val written = track.write(chunk, offset, chunk.size - offset)
                            if (written > 0) {
                                offset += written
                            } else {
                                break
                            }
                        }
                    }
                } else {
                    if (_isAiSpeaking.value && audioQueue.isEmpty()) {
                        _isAiSpeaking.value = false
                        _currentRms.value = 0f
                    }
                    kotlinx.coroutines.delay(10)
                }
            }
        }

        return true
    }

    /**
     * Enqueues incoming PCM chunks from the server for streaming playback.
     */
    fun enqueueAudioChunk(pcmChunk: ByteArray) {
        if (pcmChunk.isNotEmpty()) {
            audioQueue.offer(pcmChunk)
        }
    }

    /**
     * Critical full-duplex Barge-in (打断机制):
     * Discards all queued chunks and flushes the AudioTrack immediately.
     */
    fun interrupt() {
        audioQueue.clear()
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error flushing AudioTrack on interrupt: ${e.message}")
        }
        _isAiSpeaking.value = false
        _currentRms.value = 0f
    }

    fun toggleSpeakerphone(): Boolean {
        val newState = !_isSpeakerphoneOn.value
        applySpeakerphoneMode(newState)
        _isSpeakerphoneOn.value = newState
        return newState
    }

    private fun applySpeakerphoneMode(speakerOn: Boolean) {
        try {
            audioManager.mode = if (speakerOn) {
                AudioManager.MODE_NORMAL
            } else {
                AudioManager.MODE_IN_COMMUNICATION
            }
            audioManager.isSpeakerphoneOn = speakerOn
        } catch (e: Exception) {
            Log.w(tag, "Error toggling speakerphone: ${e.message}")
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        interrupt()

        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            } catch (e: Exception) {
                Log.w(tag, "Error releasing AudioTrack: ${e.message}")
            }
        }
        audioTrack = null
        _isAiSpeaking.value = false
        _currentRms.value = 0f
    }

    private fun calculateNormalizedRms(bytes: ByteArray): Float {
        var sumSquares = 0.0
        val sampleCount = bytes.size / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until sampleCount) {
            val sample = (bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)
            val normalized = sample.toShort() / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / sampleCount).toFloat()
        return (rms * 3.5f).coerceIn(0f, 1f)
    }
}
