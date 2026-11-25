package space.app.voice

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Everything voice works at 16 kHz mono, what Moonshine and Silero expect. */
const val SAMPLE_RATE = 16_000

/** 16-bit PCM WAV → samples in [-1, 1]. Reads the first "data" chunk; assumes 16 kHz mono. */
fun readWav(bytes: ByteArray): FloatArray {
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var pos = 12
    while (pos + 8 <= bytes.size) {
        val id = String(bytes, pos, 4, Charsets.US_ASCII)
        val size = buf.getInt(pos + 4)
        if (id == "data") {
            val n = minOf(size, bytes.size - pos - 8) / 2
            return FloatArray(n) { buf.getShort(pos + 8 + it * 2) / 32768f }
        }
        pos += 8 + size + (size and 1)
    }
    return FloatArray(0)
}

/** Samples → 16-bit PCM WAV, so the original recording can be kept with its note. */
fun writeWav(samples: FloatArray, out: OutputStream) {
    val data = samples.size * 2
    val buf = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
    buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
    buf.put("data".toByteArray()).putInt(data)
    samples.forEach { buf.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
    out.write(buf.array())
}
