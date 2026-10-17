package com.riffado.meetings.ai

import org.junit.Assert.*
import org.junit.Test

class AudioDecoderTest {
    @Test fun `stereo downmix averages channels`() {
        assertArrayEquals(floatArrayOf(0f, 0.5f), AudioSamples.downmix(floatArrayOf(1f, -1f, 0.25f, 0.75f), 2), 0.0001f)
    }
    @Test fun `mono preserves samples and clamps range`() {
        assertArrayEquals(floatArrayOf(-1f, 0.5f, 1f), AudioSamples.downmix(floatArrayOf(-2f, 0.5f, 2f), 1), 0f)
    }
    @Test fun `linear upsample interpolates`() {
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f, 1f), AudioSamples.resample(floatArrayOf(0f, 1f), 8000), 0.0001f)
    }
    @Test fun `downsample retains correct duration`() {
        assertArrayEquals(floatArrayOf(0f, 0.5f), AudioSamples.resample(floatArrayOf(0f, 0.25f, 0.5f, 0.75f), 32000), 0.0001f)
    }
    @Test fun `empty resample returns empty`() { assertEquals(0, AudioSamples.resample(floatArrayOf(), 44100).size) }
    @Test fun `trim uses presentation timestamp and exclusive end`() {
        assertArrayEquals(floatArrayOf(0.2f, 0.3f), AudioSamples.trimWindow(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), 1000, 10000, 11000, 13000), 0f)
    }
    @Test fun `window outside buffer returns empty`() {
        assertEquals(0, AudioSamples.trimWindow(FloatArray(10), 16000, 0, 1000000, 2000000).size)
        assertEquals(0, AudioSamples.trimWindow(FloatArray(10), 16000, 2000000, 0, 1000000).size)
    }
    @Test fun `fractional sample boundaries round up`() {
        assertArrayEquals(floatArrayOf(2f, 3f), AudioSamples.trimWindow(floatArrayOf(1f, 2f, 3f, 4f), 1000, 0, 500, 2500), 0f)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `invalid channel count rejected`() { AudioSamples.downmix(floatArrayOf(1f), 0) }
}
