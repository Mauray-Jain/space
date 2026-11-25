package space.app.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import space.app.understand.OcrLine

/**
 * PaddleOCR on ONNX Runtime (~10 MB): a detector finds text boxes, a recogniser reads each.
 * Screenshots are upright, so boxes are axis-aligned. Create it for one image and close it
 * right after (CLAUDE.md).
 */
class Ocr(det: ByteArray, rec: ByteArray, private val chars: List<String>) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val det: OrtSession = env.createSession(det)
    private val rec: OrtSession = env.createSession(rec)

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** The text lines, top to bottom, with their height in px (bigger usually means a title). */
    fun read(image: Rgb): List<OcrLine> {
        val boxes = detect(image)
        val read = boxes.mapNotNull { b -> recognise(image.crop(b.left, b.top, b.right, b.bottom)).takeIf { it.isNotBlank() }?.let { b to it } }
        // Boxes that share most of their height are one line, read left to right.
        val lines = mutableListOf<MutableList<Pair<Box, String>>>()
        for (entry in read.sortedBy { it.first.top }) {
            val line = lines.lastOrNull()
            val b = entry.first
            val l = line?.first()?.first
            if (l != null && minOf(b.bottom, l.bottom) - maxOf(b.top, l.top) > 0.5 * minOf(b.bottom - b.top, l.bottom - l.top)) line.add(entry)
            else lines += mutableListOf(entry)
        }
        return lines.map { line ->
            OcrLine(line.sortedBy { it.first.left }.joinToString(" ") { it.second }, line.maxOf { it.first.bottom - it.first.top })
        }
    }

    private fun detect(image: Rgb): List<Box> {
        // Longest side up to 960 px, both sides multiples of 32 (what the detector expects).
        val scale = minOf(1f, 960f / maxOf(image.width, image.height))
        val w = maxOf(32, Math.round(image.width * scale / 32) * 32)
        val h = maxOf(32, Math.round(image.height * scale / 32) * 32)
        val small = image.resize(w, h)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val input = FloatArray(3 * w * h)
        for (i in 0 until w * h) for (c in 0..2) {
            val v = ((small.pixels[i] shr (16 - 8 * c)) and 0xFF) / 255f
            input[c * w * h + i] = (v - mean[c]) / std[c]
        }
        val prob = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, h.toLong(), w.toLong())).use { x ->
            det.run(mapOf("x" to x)).use { out -> (out.get(0) as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } } }
        }
        val sx = image.width.toFloat() / w
        val sy = image.height.toFloat() / h
        return regions(prob, w, h).map { r ->
            // Grow each box a little: the detector marks the core of the text (PaddleOCR "unclip").
            val bw = r.right - r.left
            val bh = r.bottom - r.top
            val grow = bw * bh * 1.5f / (2f * (bw + bh))
            Box(
                ((r.left - grow) * sx).toInt().coerceAtLeast(0),
                ((r.top - grow) * sy).toInt().coerceAtLeast(0),
                ((r.right + grow) * sx).toInt().coerceAtMost(image.width),
                ((r.bottom + grow) * sy).toInt().coerceAtMost(image.height),
            )
        }.filter { it.right - it.left > 2 && it.bottom - it.top > 2 }
    }

    /** Connected areas of likely text in the probability map. */
    private fun regions(prob: FloatArray, w: Int, h: Int): List<Box> {
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val boxes = mutableListOf<Box>()
        for (start in 0 until w * h) {
            if (seen[start] || prob[start] <= THRESHOLD) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            var left = w; var top = h; var right = 0; var bottom = 0; var sum = 0f
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                left = minOf(left, x); right = maxOf(right, x + 1); top = minOf(top, y); bottom = maxOf(bottom, y + 1)
                sum += prob[p]
                for (n in intArrayOf(p - 1, p + 1, p - w, p + w)) {
                    if (n < 0 || n >= w * h || seen[n] || prob[n] <= THRESHOLD) continue
                    if ((n == p - 1 && x == 0) || (n == p + 1 && x == w - 1)) continue
                    seen[n] = true; queue[tail++] = n
                }
            }
            if (tail >= MIN_PIXELS && sum / tail > BOX_THRESHOLD) boxes += Box(left, top, right, bottom)
        }
        return boxes
    }

    private fun recognise(line: Rgb): String {
        val h = 48
        val w = maxOf(8, minOf(line.width * h / maxOf(1, line.height), 2000))
        val img = line.resize(w, h)
        val input = FloatArray(3 * w * h)
        for (i in 0 until w * h) for (c in 0..2) input[c * w * h + i] = ((img.pixels[i] shr (16 - 8 * c)) and 0xFF) / 127.5f - 1f
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, h.toLong(), w.toLong())).use { x ->
            rec.run(mapOf("x" to x)).use { out ->
                val t = out.get(0) as OnnxTensor
                val (steps, classes) = t.info.shape.let { it[1].toInt() to it[2].toInt() }
                val b = t.floatBuffer
                // Greedy CTC: best class per step, drop repeats and blanks (class 0). The last class is a space.
                val text = StringBuilder()
                var previous = 0
                for (s in 0 until steps) {
                    var best = 0
                    for (c in 1 until classes) if (b.get(s * classes + c) > b.get(s * classes + best)) best = c
                    if (best != 0 && best != previous) text.append(chars.getOrNull(best - 1)?.takeIf { it.isNotEmpty() } ?: " ")
                    previous = best
                }
                text.toString().trim()
            }
        }
    }

    override fun close() {
        det.close()
        rec.close()
    }

    private companion object {
        const val THRESHOLD = 0.3f
        const val BOX_THRESHOLD = 0.5f
        const val MIN_PIXELS = 10
    }
}
