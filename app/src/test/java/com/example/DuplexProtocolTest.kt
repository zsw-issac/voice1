package com.example

import com.example.network.DuplexProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DuplexProtocolTest {

    @Test
    fun testPingMessageFormat() {
        val pingJson = DuplexProtocol.buildPing(123456789L)
        val json = JSONObject(pingJson)
        assertEquals("ping", json.getString("type"))
        assertEquals(123456789L, json.getLong("ts"))
    }

    @Test
    fun testInterruptMessageFormat() {
        val interruptJson = DuplexProtocol.buildInterrupt()
        val json = JSONObject(interruptJson)
        assertEquals("response.cancel", json.getString("type"))
    }

    @Test
    fun testBinaryDownlinkFrameParsing() {
        // Construct Scheme A binary frame:
        // Byte 0: isLast=true (0x80) | responseId=5 (0x05) => 0x85
        // Byte 1..4: sample PCM bytes
        val testBytes = byteArrayOf(0x85.toByte(), 0x12, 0x34, 0x56, 0x78)
        val parsed = DuplexProtocol.parseDownlinkBinaryFrame(testBytes)

        assertTrue(parsed != null)
        assertTrue(parsed!!.isLast)
        assertEquals(5, parsed.responseId)
        assertEquals(4, parsed.pcmAudio.size)
        assertEquals(0x12.toByte(), parsed.pcmAudio[0])
    }

    @Test
    fun testBinaryDownlinkFrameNotLast() {
        // Byte 0: isLast=false (0x00) | responseId=12 (0x0C) => 0x0C
        val testBytes = byteArrayOf(0x0C.toByte(), 0xAA.toByte(), 0xBB.toByte())
        val parsed = DuplexProtocol.parseDownlinkBinaryFrame(testBytes)

        assertTrue(parsed != null)
        assertFalse(parsed!!.isLast)
        assertEquals(12, parsed.responseId)
        assertEquals(2, parsed.pcmAudio.size)
    }
}
