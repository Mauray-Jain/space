package space.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import space.app.voice.SAMPLE_RATE

/** Microphone capture at 16 kHz mono. Pauses music while recording and resumes it after (audio focus). */
@Singleton
class Recorder @Inject constructor(@ApplicationContext context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    /** EDGE_CASES.md §5: no recording during a phone call. */
    fun inCall() = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION

    /** Starts recording; the caller checked RECORD_AUDIO. Stop it with [Recording.stop]. */
    @SuppressLint("MissingPermission")
    fun start(): Recording {
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).build())
            .build()
        audio.requestAudioFocus(focus)
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // The system's default input, so a Bluetooth headset mic works too.
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 4)
        return Recording(record) { audio.abandonAudioFocusRequest(focus) }
    }
}

class Recording internal constructor(private val record: AudioRecord, private val release: () -> Unit) {
    @Volatile private var running = true
    private val chunks = mutableListOf<ShortArray>()
    private val thread = Thread {
        val buffer = ShortArray(SAMPLE_RATE / 10)
        record.startRecording()
        var total = 0
        while (running && total < MAX_SAMPLES) {
            val n = record.read(buffer, 0, buffer.size)
            if (n > 0) { synchronized(chunks) { chunks += buffer.copyOf(n) }; total += n }
        }
    }.apply { start() }

    /** Stops, releases the microphone and audio focus, and returns what was heard. */
    fun stop(): FloatArray {
        running = false
        thread.join()
        record.stop()
        record.release()
        release()
        val all = synchronized(chunks) { chunks.toList() }
        val out = FloatArray(all.sumOf { it.size })
        var i = 0
        all.forEach { c -> c.forEach { out[i++] = it / 32768f } }
        return out
    }

    private companion object {
        const val MAX_SAMPLES = 5 * 60 * SAMPLE_RATE // 5 minutes
    }
}
