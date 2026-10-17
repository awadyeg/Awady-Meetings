package com.riffado.meetings.ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed interface SpeakerModelState {
    data object Idle : SpeakerModelState
    data class Preparing(val bytes: Long, val total: Long) : SpeakerModelState
    data class Downloading(val bytes: Long, val total: Long) : SpeakerModelState
    data object Ready : SpeakerModelState
    data class Error(val message: String) : SpeakerModelState
}

class SpeakerModelManager(context: Context) {
    private val assets = context.applicationContext.assets
    val modelFile = File(context.filesDir, "speaker-models/$MODEL_NAME")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var preparation: Deferred<File>? = null
    private val mutableState = MutableStateFlow<SpeakerModelState>(if (isReady()) SpeakerModelState.Ready else SpeakerModelState.Idle)
    val state = mutableState.asStateFlow()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun isReady(): Boolean = modelFile.isFile && modelFile.length() == EXPECTED_SIZE

    suspend fun ensureModel(): File {
        val task = mutex.withLock {
            preparation?.takeIf { it.isActive } ?: scope.async { prepareModel() }.also { preparation = it }
        }
        return task.await()
    }

    private suspend fun prepareModel(): File {
        if (isReady()) {
            mutableState.value = SpeakerModelState.Ready
            return modelFile
        }
        val part = File(modelFile.parentFile, "$MODEL_NAME.part")
        try {
            check(modelFile.parentFile!!.isDirectory || modelFile.parentFile!!.mkdirs()) { "Cannot create model directory" }
            if (!copyAsset(part)) downloadWithRetries(part)
            check(part.length() == EXPECTED_SIZE) { "Incomplete voice model; please retry" }
            Files.move(part.toPath(), modelFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            mutableState.value = SpeakerModelState.Ready
            return modelFile
        } catch (e: CancellationException) {
            mutableState.value = SpeakerModelState.Idle
            throw e
        } catch (e: Exception) {
            mutableState.value = SpeakerModelState.Error(e.message ?: "Voice model preparation failed")
            throw e
        }
    }

    private suspend fun copyAsset(part: File): Boolean {
        val input = try {
            assets.open(MODEL_NAME)
        } catch (_: IOException) {
            return false
        }
        mutableState.value = SpeakerModelState.Preparing(0, EXPECTED_SIZE)
        try {
            input.use { copy(it, part, 0, preparing = true) }
        } catch (_: AssetReadException) {
            check(part.delete() || !part.exists()) { "Cannot discard unreadable voice model" }
            return false
        }
        return true
    }

    private suspend fun downloadWithRetries(part: File) {
        for (attempt in 0..3) {
            try {
                download(part)
                return
            } catch (e: IOException) {
                if (attempt == 3) throw e
                delay(2_000L shl attempt)
            }
        }
    }

    private suspend fun download(part: File) {
        if (part.length() > EXPECTED_SIZE) {
            check(part.delete()) { "Cannot discard oversized voice model" }
        }
        val offset = part.length()
        mutableState.value = SpeakerModelState.Downloading(offset, EXPECTED_SIZE)
        val request = Request.Builder().url(MODEL_URL)
            .header("Accept-Encoding", "identity")
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()
        client.newCall(request).execute().use { response ->
            val start = modelDownloadOffset(response.code, offset, EXPECTED_SIZE, response.header("Content-Range"))
            if (response.code == 416) return
            val body = response.body ?: throw IOException("Empty model download")
            mutableState.value = SpeakerModelState.Downloading(start, EXPECTED_SIZE)
            body.byteStream().use { copy(it, part, start, preparing = false) }
        }
        if (part.length() != EXPECTED_SIZE) throw IOException("Incomplete model download; please retry")
    }

    private suspend fun copy(input: InputStream, part: File, offset: Long, preparing: Boolean) {
        FileOutputStream(part, offset > 0).use { output ->
            val buffer = ByteArray(64 * 1024)
            var bytes = offset
            while (true) {
                coroutineContext.ensureActive()
                val count = try {
                    input.read(buffer)
                } catch (e: IOException) {
                    if (preparing) throw AssetReadException(e) else throw e
                }
                if (count < 0) break
                bytes += count
                check(bytes <= EXPECTED_SIZE) { "Unexpected voice model size" }
                output.write(buffer, 0, count)
                mutableState.value = if (preparing) SpeakerModelState.Preparing(bytes, EXPECTED_SIZE)
                    else SpeakerModelState.Downloading(bytes, EXPECTED_SIZE)
            }
            output.fd.sync()
        }
    }

    private class AssetReadException(cause: IOException) : IOException(cause)

    companion object {
        const val MODEL_NAME = "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"
        const val EXPECTED_SIZE = 26_485_263L
        private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/$MODEL_NAME"
    }
}
