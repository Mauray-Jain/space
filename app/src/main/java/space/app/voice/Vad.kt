package space.app.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** Silero VAD v5 (~2 MB). Create it for one recording and close it right after (CLAUDE.md). */
class Vad(model: ByteArray) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(model)

    /** Speech probability for every 512-sample (32 ms) chunk. */
    fun probabilities(samples: FloatArray): FloatArray {
        var state = FloatArray(2 * 128)
        val context = FloatArray(CONTEXT)
        val sr = OnnxTensor.createTensor(env, longArrayOf(SAMPLE_RATE.toLong()).let { java.nio.LongBuffer.wrap(it) }, longArrayOf())
        return sr.use {
            FloatArray(samples.size / CHUNK) { i ->
                val input = context + samples.copyOfRange(i * CHUNK, (i + 1) * CHUNK)
                samples.copyInto(context, 0, (i + 1) * CHUNK - CONTEXT, (i + 1) * CHUNK)
                OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, input.size.toLong())).use { x ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(state), longArrayOf(2, 1, 128)).use { s ->
                        session.run(mapOf("input" to x, "state" to s, "sr" to sr)).use { out ->
                            state = (out.get("stateN").get() as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                            (out.get("output").get() as OnnxTensor).floatBuffer.get(0)
                        }
                    }
                }
            }
        }
    }

    /**
     * The recording's speech, without the silence around it, in pieces of at most [MAX_PIECE]
     * cut at pauses. Null when there's under a second of speech (an accidental press, EDGE_CASES.md §5).
     */
    fun speech(samples: FloatArray): List<FloatArray>? {
        val isSpeech = probabilities(samples).map { it > THRESHOLD }
        if (isSpeech.count { it } * CHUNK < SAMPLE_RATE) return null

        // Stretches of speech, in chunks, bridging pauses shorter than MIN_PAUSE.
        val runs = mutableListOf<IntRange>()
        var start = -1
        var lastSpeech = -1
        isSpeech.forEachIndexed { i, s ->
            if (!s) return@forEachIndexed
            if (start >= 0 && i - lastSpeech > MIN_PAUSE) { runs += start..lastSpeech; start = i }
            if (start < 0) start = i
            lastSpeech = i
        }
        runs += start..lastSpeech

        // Join stretches into pieces up to MAX_PIECE; a stretch longer than that is cut hard.
        val pieces = mutableListOf<IntRange>()
        for (run in runs) {
            val last = pieces.lastOrNull()
            if (last != null && (run.last + 1 - last.first) * CHUNK + 2 * PAD <= MAX_PIECE) {
                pieces[pieces.lastIndex] = last.first..run.last
            } else {
                var from = run.first
                val step = (MAX_PIECE - 2 * PAD) / CHUNK
                while (run.last - from + 1 > step) { pieces += from until from + step; from += step }
                pieces += from..run.last
            }
        }
        return pieces.map { r -> samples.copyOfRange(maxOf(0, r.first * CHUNK - PAD), minOf(samples.size, (r.last + 1) * CHUNK + PAD)) }
    }

    override fun close() = session.close()

    companion object {
        /** Moonshine is most accurate on pieces up to about 20 s. */
        const val MAX_PIECE = 20 * SAMPLE_RATE
        private const val CHUNK = 512
        private const val CONTEXT = 64
        private const val THRESHOLD = 0.5f
        private const val PAD = SAMPLE_RATE / 4
        private const val MIN_PAUSE = 10 // chunks, ~0.3 s
    }
}
