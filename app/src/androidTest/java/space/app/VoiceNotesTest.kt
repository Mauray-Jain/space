package space.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import space.app.voice.SAMPLE_RATE
import space.app.voice.VoiceNotes
import space.app.voice.readWav

// The real models from the APK's assets, on Android's ONNX Runtime build (PLAN.md §7).
@RunWith(AndroidJUnit4::class)
class VoiceNotesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val notes = VoiceNotes(instrumentation.targetContext)

    @Test
    fun transcribesOnTheDevice() = runBlocking {
        val clip = instrumentation.context.assets.open("librispeech.wav").use { readWav(it.readBytes()) }
        val text = notes.transcribe(FloatArray(SAMPLE_RATE) + clip)!!.lowercase().replace(Regex("[^a-z ]"), "")
        assertEquals("after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels", text)
    }

    @Test
    fun silenceGivesNothing() = runBlocking { assertNull(notes.transcribe(FloatArray(3 * SAMPLE_RATE))) }
}
