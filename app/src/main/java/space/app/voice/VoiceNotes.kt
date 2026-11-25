package space.app.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Speech → text on the phone. The models are loaded for one recording and closed right after (CLAUDE.md). */
@Singleton
class VoiceNotes @Inject constructor(@ApplicationContext private val context: Context) {
    private fun asset(name: String) = context.assets.open(name).use { it.readBytes() }

    /** The transcript, or null when there was no real speech (EDGE_CASES.md §5). */
    suspend fun transcribe(samples: FloatArray): String? = withContext(Dispatchers.Default) {
        val pieces = Vad(asset("silero-vad.onnx")).use { it.speech(samples) } ?: return@withContext null
        Transcriber(asset("moonshine-encoder.onnx"), asset("moonshine-decoder.onnx"), readTokens(asset("moonshine-tokens.txt").decodeToString()))
            .use { t -> pieces.joinToString(" ") { t.transcribe(it) } }
            .trim()
            .ifEmpty { null }
    }
}
