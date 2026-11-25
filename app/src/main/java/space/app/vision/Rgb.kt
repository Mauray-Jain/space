package space.app.vision

/** An image as ARGB pixels, row by row. Plain data, so vision code runs (and is tested) off Android too. */
class Rgb(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(pixels.size == width * height) }

    operator fun get(x: Int, y: Int) = pixels[y * width + x]

    fun crop(left: Int, top: Int, right: Int, bottom: Int): Rgb {
        val w = right - left
        val h = bottom - top
        return Rgb(w, h, IntArray(w * h) { i -> this[left + i % w, top + i / w] })
    }

    /** Bilinear resize. */
    fun resize(w: Int, h: Int): Rgb {
        val out = IntArray(w * h)
        val sx = width.toFloat() / w
        val sy = height.toFloat() / h
        for (y in 0 until h) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, height - 1f)
            val y0 = fy.toInt(); val y1 = minOf(y0 + 1, height - 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, width - 1f)
                val x0 = fx.toInt(); val x1 = minOf(x0 + 1, width - 1); val tx = fx - x0
                var p = 0xFF000000.toInt()
                for (shift in intArrayOf(16, 8, 0)) {
                    fun c(px: Int) = (px shr shift) and 0xFF
                    val top = c(this[x0, y0]) * (1 - tx) + c(this[x1, y0]) * tx
                    val bottom = c(this[x0, y1]) * (1 - tx) + c(this[x1, y1]) * tx
                    p = p or ((top * (1 - ty) + bottom * ty).toInt().coerceIn(0, 255) shl shift)
                }
                out[y * w + x] = p
            }
        }
        return Rgb(w, h, out)
    }
}
