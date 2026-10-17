package com.riffado.meetings.ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed interface SpeakerModelState {
    data object Idle : SpeakerModelState
    data class Downloading(val bytes: Long, val total: Long) : SpeakerModelState
    data object Ready : SpeakerModelState
    data class Error(val message: String) : SpeakerModelState
}

class SpeakerModelManager(context: Context) {
    val modelFile = File(context.filesDir, "speaker-models/$MODEL_NAME")
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<SpeakerModelState>(if (isReady()) SpeakerModelState.Ready else SpeakerModelState.Idle)
    val state = mutableState.asStateFlow()
    private val client = OkHttpClient.Builder().followRedirects(true).callTimeout(10, TimeUnit.MINUTES).build()

    fun isReady(): Boolean = modelFile.isFile && modelFile.length() == EXPECTED_SIZE

    suspend fun ensureDownloaded(): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isReady()) {
                mutableState.value = SpeakerModelState.Ready
                return@withLock modelFile
            }
            val part = File(modelFile.parentFile, "$MODEL_NAME.part")
            try {
                check(modelFile.parentFile!!.isDirectory || modelFile.parentFile!!.mkdirs()) { "Cannot create model directory" }
                mutableState.value = SpeakerModelState.Downloading(0, EXPECTED_SIZE)
                client.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { response ->
                    check(response.isSuccessful) { "Model download failed: HTTP ${response.code}" }
                    val body = response.body ?: error("Empty model download")
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytes = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytes += count
                                check(bytes <= EXPECTED_SIZE) { "Unexpected model size" }
                                output.write(buffer, 0, count)
                                mutableState.value = SpeakerModelState.Downloading(bytes, EXPECTED_SIZE)
                            }
                            output.fd.sync()
                        }
                    }
                }
                check(part.length() == EXPECTED_SIZE) { "Incomplete model download; please retry" }
                Files.move(part.toPath(), modelFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                mutableState.value = SpeakerModelState.Ready
                modelFile
            } catch (e: CancellationException) {
                part.delete()
                mutableState.value = SpeakerModelState.Idle
                throw e
            } catch (e: Exception) {
                part.delete()
                mutableState.value = SpeakerModelState.Error(e.message ?: "Model download failed")
                throw e
            }
        }
    }

    companion object {
        const val MODEL_NAME = "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"
        const val EXPECTED_SIZE = 26_485_263L
        private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/$MODEL_NAME"
    }
}
