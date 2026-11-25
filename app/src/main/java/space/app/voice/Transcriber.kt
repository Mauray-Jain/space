package space.app.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.ByteArrayOutputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Moonshine Base (English, int8, ~63 MB) on ONNX Runtime: encoder, then greedy decoding with
 * the merged decoder and its key/value cache. Create it for one recording and close it right
 * after, so the model is only in memory while it's used (CLAUDE.md).
 */
class Transcriber(encoder: ByteArray, decoder: ByteArray, private val tokens: List<String>) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val encoder: OrtSession = env.createSession(encoder)
    private val decoder: OrtSession = env.createSession(decoder)
    private val pastNames = this.decoder.inputNames.filter { it.startsWith("past_key_values.") }
    // [batch, heads, length, head size]; heads and head size differ between Tiny and Base.
    private val pastShape = (this.decoder.inputInfo.getValue(pastNames.first()).info as ai.onnxruntime.TensorInfo).shape

    fun transcribe(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val hidden = OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), longArrayOf(1, samples.size.toLong())).use { x ->
            encoder.run(mapOf("input_values" to x)).use { (it.get(0) as OnnxTensor).let { t -> copy(t) } }
        }
        val ids = mutableListOf(BOS)
        // About 6.5 tokens per second of speech is plenty (Moonshine's own limit).
        val maxTokens = (samples.size / SAMPLE_RATE.toFloat() * 6.5f).toInt() + 10
        var past: Map<String, OnnxTensor> = pastNames.associateWith { empty() }
        try {
            hidden.use {
                for (step in 0 until maxTokens) {
                    val input = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(ids.last().toLong())), longArrayOf(1, 1))
                    val useCache = OnnxTensor.createTensor(env, booleanArrayOf(step > 0))
                    val result = decoder.run(past + mapOf("input_ids" to input, "encoder_hidden_states" to hidden, "use_cache_branch" to useCache))
                    input.close(); useCache.close()
                    val next = argmaxLast(result.get("logits").get() as OnnxTensor)
                    // The encoder's cache is computed once on the first step; the decoder's grows every step.
                    val updated = pastNames.associateWith { name ->
                        val present = name.replace("past_key_values", "present")
                        if (step == 0 || ".decoder." in name) copy(result.get(present).get() as OnnxTensor) else past.getValue(name)
                    }
                    past.forEach { (name, t) -> if (updated[name] !== t) t.close() }
                    past = updated
                    result.close()
                    if (next == EOS) break
                    ids += next
                }
            }
        } finally {
            past.values.forEach { it.close() }
        }
        return decodeTokens(ids.drop(1), tokens)
    }

    private fun empty() = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), longArrayOf(1, pastShape[1], 0, pastShape[3]))

    /** Results are closed every step, so keep what's needed in tensors of our own. */
    private fun copy(t: OnnxTensor): OnnxTensor {
        val b = t.floatBuffer
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(b.remaining()).also { b.get(it) }), t.info.shape)
    }

    private fun argmaxLast(logits: OnnxTensor): Int {
        val b = logits.floatBuffer
        val vocab = logits.info.shape.last().toInt()
        val start = b.remaining() - vocab
        var best = 0
        for (i in 1 until minOf(vocab, tokens.size)) if (b.get(start + i) > b.get(start + best)) best = i
        return best
    }

    override fun close() {
        encoder.close()
        decoder.close()
    }

    private companion object {
        const val BOS = 1
        const val EOS = 2
    }
}

/** Token ids → text: "▁" is a space, "<0xNN>" tokens are UTF-8 bytes. */
fun decodeTokens(ids: List<Int>, tokens: List<String>): String {
    val bytes = ByteArrayOutputStream()
    val byte = Regex("""<0x([0-9A-Fa-f]{2})>""")
    for (id in ids) {
        val t = tokens.getOrNull(id) ?: continue
        if (id <= 2) continue
        val m = byte.matchEntire(t)
        if (m != null) bytes.write(m.groupValues[1].toInt(16)) else bytes.write(t.replace("▁", " ").toByteArray())
    }
    return bytes.toString(Charsets.UTF_8.name()).trim()
}

/** moonshine-tokens.txt (made by the build): one token per line, "\n" and "\\" escaped. */
fun readTokens(text: String): List<String> = text.split('\n').map { line ->
    val out = StringBuilder()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '\\' && i + 1 < line.length) {
            out.append(when (line[i + 1]) { 'n' -> '\n'; 'r' -> '\r'; else -> line[i + 1] })
            i += 2
        } else {
            out.append(c); i++
        }
    }
    out.toString()
}
