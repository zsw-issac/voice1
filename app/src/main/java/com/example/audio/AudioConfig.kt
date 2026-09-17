package com.example.audio

import android.media.AudioFormat

data class AudioConfig(
    val uplinkSampleRate: Int = 16000,
    val downlinkSampleRate: Int = 24000,
    val frameDurationMs: Int = 40,
    val channelConfigIn: Int = AudioFormat.CHANNEL_IN_MONO,
    val channelConfigOut: Int = AudioFormat.CHANNEL_OUT_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val vadEnergyThreshold: Float = 0.08f,
    val autoInterruptOnSpeech: Boolean = true
) {
    // Number of 16-bit samples in one uplink frame (16000 * 40 / 1000 = 640 samples)
    val uplinkSamplesPerFrame: Int
        get() = (uplinkSampleRate * frameDurationMs) / 1000

    // Number of bytes per uplink frame (640 * 2 = 1280 bytes)
    val uplinkBytesPerFrame: Int
        get() = uplinkSamplesPerFrame * 2

    // Number of 16-bit samples in one downlink frame (24000 * 40 / 1000 = 960 samples)
    val downlinkSamplesPerFrame: Int
        get() = (downlinkSampleRate * frameDurationMs) / 1000

    // Number of bytes per downlink frame (960 * 2 = 1920 bytes)
    val downlinkBytesPerFrame: Int
        get() = downlinkSamplesPerFrame * 2

    // Backward-compatibility properties
    val sampleRate: Int
        get() = uplinkSampleRate

    val bytesPerFrame: Int
        get() = uplinkBytesPerFrame
}
