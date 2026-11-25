package space.app.voice

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Runs the real models (downloaded by the build) on ONNX Runtime's desktop build. */
class VoiceTest {
    companion object {
        private val models = File(System.getProperty("models.dir")!!)
        private val clip = readWav(File(System.getProperty("testAudio.dir")!!, "librispeech.wav").readBytes())
        private lateinit var vad: Vad
        private lateinit var transcriber: Transcriber

        @BeforeClass @JvmStatic
        fun load() {
            vad = Vad(File(models, "silero-vad.onnx").readBytes())
            transcriber = Transcriber(
                File(models, "moonshine-encoder.onnx").readBytes(),
                File(models, "moonshine-decoder.onnx").readBytes(),
                readTokens(File(models, "moonshine-tokens.txt").readText()),
            )
        }

        @AfterClass @JvmStatic
        fun unload() {
            vad.close()
            transcriber.close()
        }
    }

    private fun silence(seconds: Double) = FloatArray((seconds * SAMPLE_RATE).toInt())
    private fun words(s: String) = s.lowercase().replace(Regex("""[^a-z ]"""), "").trim()

    @Test
    fun wavRoundTrip() {
        val out = ByteArrayOutputStream()
        writeWav(floatArrayOf(0f, 0.5f, -0.5f), out)
        val back = readWav(out.toByteArray())
        assertEquals(3, back.size)
        assertEquals(0.5f, back[1], 0.001f)
    }

    @Test
    fun transcribesSpeech() = assertEquals(
        "after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels",
        words(transcriber.transcribe(clip)),
    )

    // Silence around the speech is cut before transcribing.
    @Test
    fun trimsSilence() {
        val speech = vad.speech(silence(2.0) + clip + silence(2.0))!!.single()
        assertTrue("${speech.size}", speech.size < clip.size + SAMPLE_RATE)
        assertTrue("${speech.size}", speech.size > clip.size - SAMPLE_RATE)
    }

    // EDGE_CASES.md §5: an accidental press with no speech saves nothing.
    @Test
    fun silenceIsDiscarded() = assertNull(vad.speech(silence(3.0)))

    @Test
    fun underASecondOfSpeechIsDiscarded() = assertNull(vad.speech(silence(1.0) + clip.copyOfRange(16000, 24000) + silence(1.0)))

    // A long ramble is cut at pauses into pieces Moonshine handles well, and nothing is lost.
    @Test
    fun longRecordingIsSplitAtPauses() {
        val ramble = (1..6).fold(FloatArray(0)) { acc, _ -> acc + clip + silence(0.8) }
        val pieces = vad.speech(ramble)!!
        assertTrue("${pieces.size}", pieces.size >= 2)
        assertTrue(pieces.all { it.size <= Vad.MAX_PIECE })
        val text = words(pieces.joinToString(" ") { transcriber.transcribe(it) })
        assertEquals(6, Regex("yellow lamps").findAll(text).count())
    }

    @Test
    fun byteFallbackTokensJoinIntoUtf8() {
        val tokens = listOf("<unk>", "<s>", "</s>", "▁Price", "<0xE2>", "<0x82>", "<0xB9>", "5")
        assertEquals("Price₹5", decodeTokens(listOf(3, 4, 5, 6, 7), tokens))
    }

    @Test
    fun tokensFileUnescapes() = assertEquals(listOf("a", "b\nc", "d\\e"), readTokens("a\nb\\nc\nd\\\\e"))
}
