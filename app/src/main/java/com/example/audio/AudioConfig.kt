package com.example.audio

import android.media.AudioFormat

data class AudioConfig(
    val sampleRate: Int = 16000,
    val frameDurationMs: Int = 40,
    val channelConfigIn: Int = AudioFormat.CHANNEL_IN_MONO,
    val channelConfigOut: Int = AudioFormat.CHANNEL_OUT_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val vadEnergyThreshold: Float = 0.08f,
    val autoInterruptOnSpeech: Boolean = true
) {
    // Number of 16-bit samples in one frame
    val samplesPerFrame: Int
        get() = (sampleRate * frameDurationMs) / 1000

    // Number of bytes per frame (16-bit PCM mono = 2 bytes per sample)
    val bytesPerFrame: Int
        get() = samplesPerFrame * 2
}
