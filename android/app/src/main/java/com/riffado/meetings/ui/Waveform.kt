package com.riffado.meetings.ui

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Loudness envelope of a recording: one RMS value per [WINDOW_MS] across all segments. */
class Waveform(val peaks: FloatArray, val segmentWindows: IntArray) {
    val durationMs: Long get() = peaks.size.toLong() * WINDOW_MS

    /** Peaks normalised to 0..1 with a perceptual curve, resampled to [bars] buckets (max of each bucket). */
    fun bars(bars: Int): FloatArray {
        if (peaks.isEmpty() || bars <= 0) return FloatArray(bars)
        val top = peaks.max().takeIf { it > 0f } ?: 1f
        return FloatArray(bars) { b ->
            val from = (b.toLong() * peaks.size / bars).toInt()
            val to = max(from + 1, ((b + 1).toLong() * peaks.size / bars).toInt()).coerceAtMost(peaks.size)
            var m = 0f
            for (i in from until to) m = max(m, peaks[i])
            sqrt((m / top).coerceIn(0f, 1f))
        }
    }

    companion object {
        const val WINDOW_MS = 100L
    }
}

object WaveformExtractor {
    private const val TAG = "WaveformExtractor"

    suspend fun load(context: Context, recordingId: String, files: List<File>): Waveform? = withContext(Dispatchers.Default) {
        val existing = files.filter { it.exists() && it.length() > 0 }
        if (existing.isEmpty()) return@withContext null
        val key = existing.joinToString("|") { "${it.name}:${it.length()}" }.hashCode().toUInt().toString(16)
        val dir = File(context.cacheDir, "waveforms").apply { mkdirs() }
        val cache = File(dir, "$recordingId-$key.bin")
        runCatching { if (cache.exists()) return@withContext read(cache) }
        val parts = existing.map { file ->
            runCatching { extract(file) }.onFailure { Log.w(TAG, "Waveform failed for ${file.name}", it) }.getOrDefault(FloatArray(0))
        }
        val result = Waveform(parts.fold(FloatArray(0)) { a, b -> a + b }, parts.map { it.size }.toIntArray())
        runCatching {
            dir.listFiles { f -> f.name.startsWith("$recordingId-") }?.forEach { it.delete() }
            write(cache, result)
        }
        result
    }

    private fun write(file: File, wf: Waveform) = DataOutputStream(file.outputStream().buffered()).use { out ->
        out.writeInt(wf.segmentWindows.size); wf.segmentWindows.forEach(out::writeInt)
        out.writeInt(wf.peaks.size); wf.peaks.forEach(out::writeFloat)
    }

    private fun read(file: File): Waveform = DataInputStream(file.inputStream().buffered()).use { inp ->
        val segs = IntArray(inp.readInt()) { inp.readInt() }
        val peaks = FloatArray(inp.readInt()) { inp.readFloat() }
        Waveform(peaks, segs)
    }

    /** Streams one file through MediaCodec, keeping only RMS per window (no PCM is retained). */
    private suspend fun extract(file: File): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val out = ArrayList<Float>(4096)
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return FloatArray(0)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            if (mime == "audio/raw") return FloatArray(0)
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()
            var outFormat = format
            var windowSamples = 0
            var sumSq = 0.0
            var count = 0
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            var idleSince = System.nanoTime()
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (System.nanoTime() - idleSince > 20_000_000_000L) break
                if (!inputDone) {
                    val idx = decoder.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = decoder.getInputBuffer(idx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            decoder.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0); extractor.advance()
                        }
                    }
                }
                val idx = decoder.dequeueOutputBuffer(info, 10_000)
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outFormat = decoder.outputFormat
                } else if (idx >= 0) {
                    idleSince = System.nanoTime()
                    if (windowSamples == 0) {
                        val rate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val ch = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        windowSamples = max(1, (rate * ch * Waveform.WINDOW_MS / 1000).toInt())
                    }
                    if (info.size > 0) {
                        val buf = decoder.getOutputBuffer(idx)!!.duplicate().order(ByteOrder.nativeOrder())
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        val float = outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        while (buf.remaining() >= if (float) 4 else 2) {
                            val s = if (float) buf.float else buf.short / 32768f
                            sumSq += s * s; count++
                            if (count >= windowSamples) {
                                out += sqrt(sumSq / count).toFloat(); sumSq = 0.0; count = 0
                            }
                        }
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(idx, false)
                }
            }
            if (count > 0) out += sqrt(sumSq / count).toFloat()
        } finally {
            codec?.release()
            extractor.release()
        }
        return out.toFloatArray()
    }
}

/**
 * Plays a recording made of several segment files as one continuous timeline.
 * Positions exposed here are *global* (from the start of the first segment).
 */
@Stable
class PlayerController(context: Context, val paths: List<String>) {
    private val player = ExoPlayer.Builder(context).build().apply {
        setMediaItems(paths.map { MediaItem.fromUri(Uri.fromFile(File(it))) })
        prepare()
    }
    var playing by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var segmentIndex by mutableIntStateOf(0); private set
    /** Fallback segment lengths (from the waveform) used until the player knows real durations. */
    var hintSegmentMs: List<Long> = emptyList()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
    }
    init { player.addListener(listener) }

    private fun segmentDurations(): List<Long> {
        val tl: Timeline = player.currentTimeline
        val window = Timeline.Window()
        return paths.indices.map { i ->
            val real = if (i < tl.windowCount) tl.getWindow(i, window).durationMs else C.TIME_UNSET
            if (real != C.TIME_UNSET && real > 0) real else hintSegmentMs.getOrElse(i) { 0L }
        }
    }

    fun poll() {
        val d = segmentDurations()
        val idx = player.currentMediaItemIndex.coerceIn(0, max(0, paths.size - 1))
        segmentIndex = idx
        positionMs = d.take(idx).sum() + player.currentPosition.coerceAtLeast(0)
        durationMs = max(d.sum(), positionMs)
    }

    fun toggle() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) seekGlobal(0); player.play() } }
    fun play() = player.play()

    fun seekGlobal(ms: Long) {
        val d = segmentDurations()
        var rest = ms.coerceAtLeast(0)
        for (i in d.indices) {
            if (rest < d[i] || i == d.lastIndex) { player.seekTo(i, min(rest, max(0, d[i] - 1))); break }
            rest -= d[i]
        }
        poll()
    }

    /** Seeks to an offset inside a specific segment file (transcript timestamps are per segment). */
    fun seekSegment(fileName: String?, offsetMs: Long) {
        val idx = fileName?.let { n -> paths.indexOfFirst { File(it).name == n } }?.takeIf { it >= 0 } ?: 0
        player.seekTo(idx, offsetMs.coerceAtLeast(0)); poll()
    }

    /** Global position → (segment file name, offset) for highlighting the transcript line being played. */
    fun segmentPosition(): Pair<String?, Long> {
        val d = segmentDurations()
        val name = paths.getOrNull(segmentIndex)?.let { File(it).name }
        return name to (positionMs - d.take(segmentIndex).sum())
    }

    fun skip(deltaMs: Long) = seekGlobal((positionMs + deltaMs).coerceIn(0, max(0, durationMs)))

    fun release() { player.removeListener(listener); player.release() }
}

@Composable
fun rememberPlayerController(paths: List<String>): PlayerController {
    val context = LocalContext.current
    val controller = remember(paths) { PlayerController(context, paths) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    LaunchedEffect(controller) {
        while (true) { controller.poll(); delay(if (controller.playing) 50 else 300) }
    }
    return controller
}

/**
 * Scrubbable waveform: tap or drag to seek. Bars before the playhead use [played], after it [unplayed].
 * Bookmarks (fractions 0..1) are drawn as small markers above the bars.
 */
@Composable
fun WaveformScrubber(
    waveform: Waveform?,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    played: Color = Apple.colors.tint,
    unplayed: Color = Apple.colors.label.copy(alpha = 0.22f),
    markers: List<Float> = emptyList(),
    markerColor: Color = Apple.colors.orange,
    onScrub: (Boolean) -> Unit = {},
) {
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = rememberHaptics()
    fun frac(x: Float, w: Float) = (if (rtl) 1f - x / w else x / w).coerceIn(0f, 1f)
    Canvas(
        modifier
            .pointerInput(waveform) {
                detectTapGestures { haptics.selection(); onSeek(frac(it.x, size.width.toFloat())) }
            }
            .pointerInput(waveform) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = frac(it.x, size.width.toFloat()); onScrub(true) },
                    onDragEnd = { onSeek(dragFraction); dragFraction = -1f; onScrub(false) },
                    onDragCancel = { dragFraction = -1f; onScrub(false) },
                ) { change, _ -> dragFraction = frac(change.position.x, size.width.toFloat()) }
            },
    ) {
        val barW = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val count = max(1, ((size.width + gap) / (barW + gap)).toInt())
        val bars = waveform?.bars(count) ?: FloatArray(count) { 0.08f }
        val p = if (dragFraction >= 0f) dragFraction else progress.coerceIn(0f, 1f)
        val markerSpace = if (markers.isNotEmpty()) 6.dp.toPx() else 0f
        val h = size.height - markerSpace
        bars.forEachIndexed { i, v ->
            val barH = max(barW, v * h)
            val xRaw = i * (barW + gap)
            val x = if (rtl) size.width - xRaw - barW else xRaw
            val isPlayed = (i + 0.5f) / count <= p
            drawRoundRect(
                if (isPlayed) played else unplayed,
                topLeft = Offset(x, markerSpace + (h - barH) / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
        markers.forEach { m ->
            val x = (if (rtl) 1f - m else m) * size.width
            drawCircle(markerColor, radius = 2.5.dp.toPx(), center = Offset(x, 2.5.dp.toPx()))
        }
    }
}

/** Live input meter: newest level on the trailing edge, scrolling toward the leading edge. */
@Composable
fun LiveWaveform(levels: List<Float>, modifier: Modifier = Modifier, color: Color = Apple.colors.red, playhead: Color = Apple.colors.red) {
    Canvas(modifier) {
        val barW = 3.dp.toPx()
        val gap = 2.5.dp.toPx()
        val count = ((size.width / 2f) / (barW + gap)).toInt()
        val mid = size.width / 2f
        val shown = levels.takeLast(count)
        shown.forEachIndexed { i, lvl ->
            val fromEnd = shown.size - 1 - i
            val x = mid - (fromEnd + 1) * (barW + gap)
            val v = sqrt(lvl.coerceIn(0f, 1f))
            val barH = max(barW, v * size.height * 0.95f)
            val fade = (1f - fromEnd.toFloat() / count).coerceIn(0.25f, 1f)
            drawRoundRect(color.copy(alpha = color.alpha * fade), Offset(x, (size.height - barH) / 2f), Size(barW, barH), CornerRadius(barW / 2f))
        }
        // dotted "future" baseline
        var x = mid + gap
        while (x < size.width) {
            drawCircle(color.copy(alpha = 0.18f), radius = 1.dp.toPx(), center = Offset(x, size.height / 2f)); x += barW + gap
        }
        drawLine(playhead, Offset(mid, 0f), Offset(mid, size.height), strokeWidth = 2.dp.toPx())
        drawCircle(playhead, radius = 4.dp.toPx(), center = Offset(mid, 0f))
        drawCircle(playhead, radius = 4.dp.toPx(), center = Offset(mid, size.height))
    }
}
