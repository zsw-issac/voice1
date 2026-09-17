package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioCaptureManager(
    private val context: Context,
    private val config: AudioConfig,
    private val onAudioFrame: (ByteArray, Float, Boolean) -> Unit,
    private val onBargeInDetected: () -> Unit
) {
    private val tag = "AudioCaptureManager"

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _currentRms = MutableStateFlow(0f)
    val currentRms: StateFlow<Float> = _currentRms.asStateFlow()

    private val _isUserSpeaking = MutableStateFlow(false)
    val isUserSpeaking: StateFlow<Boolean> = _isUserSpeaking.asStateFlow()

    // Flag whether AI is currently speaking so we know if we should trigger barge-in
    @Volatile
    var isAiSpeaking: Boolean = false

    // VAD state trackers
    private var consecutiveSpeechFrames = 0
    private var consecutiveSilenceFrames = 0
    private val speechOnsetThresholdFrames = 3 // ~120ms of speech to confirm voice onset
    private val speechHangoverFrames = 8        // ~320ms of silence before declaring speech ended

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startRecording(coroutineScope: CoroutineScope): Boolean {
        if (_isRecording.value) return true
        if (!hasRecordPermission()) {
            Log.e(tag, "Missing RECORD_AUDIO permission")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            config.channelConfigIn,
            config.audioFormat
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(tag, "Invalid AudioRecord buffer size")
            return false
        }

        val bufferSize = maxOf(minBufferSize * 2, config.bytesPerFrame * 4)

        // Try VOICE_COMMUNICATION first for AEC, fallback to MIC
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                config.sampleRate,
                config.channelConfigIn,
                config.audioFormat,
                bufferSize
            )
        } catch (e: Exception) {
            null
        } ?: try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                config.channelConfigIn,
                config.audioFormat,
                bufferSize
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to create AudioRecord: ${e.message}")
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(tag, "AudioRecord failed to initialize")
            record.release()
            return false
        }

        audioRecord = record
        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(tag, "AudioRecord failed to start: ${e.message}")
            record.release()
            audioRecord = null
            return false
        }

        _isRecording.value = true
        consecutiveSpeechFrames = 0
        consecutiveSilenceFrames = 0

        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            val frameBytes = ByteArray(config.bytesPerFrame)
            val zeroBytes = ByteArray(config.bytesPerFrame)

            while (isActive && _isRecording.value) {
                var bytesRead = 0
                while (bytesRead < frameBytes.size && isActive && _isRecording.value) {
                    val read = record.read(
                        frameBytes,
                        bytesRead,
                        frameBytes.size - bytesRead
                    )
                    if (read > 0) {
                        bytesRead += read
                    } else if (read < 0) {
                        Log.e(tag, "AudioRecord read error: $read")
                        break
                    }
                }

                if (bytesRead == frameBytes.size) {
                    val muted = _isMuted.value
                    val finalData = if (muted) zeroBytes else frameBytes.copyOf()
                    val rms = if (muted) 0f else calculateNormalizedRms(frameBytes)

                    _currentRms.value = rms

                    // VAD Logic
                    val isSpeechFrame = !muted && rms >= config.vadEnergyThreshold
                    if (isSpeechFrame) {
                        consecutiveSpeechFrames++
                        consecutiveSilenceFrames = 0
                    } else {
                        consecutiveSilenceFrames++
                        if (consecutiveSilenceFrames >= speechHangoverFrames) {
                            consecutiveSpeechFrames = 0
                        }
                    }

                    val speakingNow = consecutiveSpeechFrames >= speechOnsetThresholdFrames
                    if (_isUserSpeaking.value != speakingNow) {
                        _isUserSpeaking.value = speakingNow
                    }

                    // Check for Barge-in (打断)
                    if (speakingNow && isAiSpeaking && config.autoInterruptOnSpeech) {
                        // User spoke while AI was speaking!
                        onBargeInDetected()
                    }

                    onAudioFrame(finalData, rms, speakingNow)
                }
            }
        }

        return true
    }

    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.w(tag, "Error releasing AudioRecord: ${e.message}")
            }
        }
        audioRecord = null
        _currentRms.value = 0f
        _isUserSpeaking.value = false
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (muted) {
            _currentRms.value = 0f
            _isUserSpeaking.value = false
        }
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
        // Clamp to 0.0f .. 1.0f
        return (rms * 3.5f).coerceIn(0f, 1f)
    }
}
