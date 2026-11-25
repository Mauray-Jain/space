package space.app.vision

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class VisionTest {
    companion object {
        private val models = File(System.getProperty("models.dir")!!)
        private lateinit var ocr: Ocr

        @BeforeClass @JvmStatic
        fun load() {
            ocr = Ocr(
                File(models, "ocr-det.onnx").readBytes(),
                File(models, "ocr-rec.onnx").readBytes(),
                File(models, "ocr-dict.txt").readText().lines(),
            )
        }

        @AfterClass @JvmStatic
        fun unload() = ocr.close()
    }

    private fun image(w: Int, h: Int, draw: (java.awt.Graphics2D) -> Unit): Rgb {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            color = Color.WHITE; fillRect(0, 0, w, h); color = Color.BLACK
            draw(this)
            dispose()
        }
        return Rgb(w, h, img.getRGB(0, 0, w, h, null, 0, w))
    }

    // A flyer: lines in reading order, the title tallest (it becomes the event title, EDGE_CASES.md §6).
    @Test
    fun readsLinesInOrderWithTheirHeights() {
        val flyer = image(720, 400) { g ->
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 48); g.drawString("SUMMER JAZZ NIGHT", 40, 80)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 32)
            g.drawString("Saturday 28 November", 40, 160)
            g.drawString("7 PM onwards, City Hall", 40, 230)
        }
        val lines = ocr.read(flyer)
        assertEquals(listOf("SUMMER JAZZ NIGHT", "Saturday 28 November", "7 PM onwards, City Hall"), lines.map { it.text })
        assertTrue(lines[0].h > lines[1].h)
    }

    @Test
    fun blankImageHasNoText() = assertEquals(emptyList<Any>(), ocr.read(image(200, 200) {}))

    @Test
    fun readsQrCodes() {
        val matrix = QRCodeWriter().encode("https://example.com/menu", BarcodeFormat.QR_CODE, 300, 300)
        val pixels = IntArray(400 * 400) { -1 }
        for (y in 0 until 300) for (x in 0 until 300) if (matrix[x, y]) pixels[(y + 50) * 400 + x + 50] = 0xFF000000.toInt()
        assertEquals(listOf("https://example.com/menu"), codes(Rgb(400, 400, pixels)))
    }

    @Test
    fun noCodesInPlainImage() = assertEquals(emptyList<String>(), codes(image(200, 200) {}))

    // Long screenshots: overlapping screens are joined without repeating the overlap.
    @Test
    fun stitchesOverlappingScreens() {
        val tall = Rgb(50, 900, IntArray(50 * 900) { i -> val y = i / 50; (y * 7919 % 255) or ((y * 31 % 255) shl 8) })
        fun window(top: Int) = Rgb(50, 400, tall.pixels.copyOfRange(top * 50, (top + 400) * 50))
        val stitched = stitch(listOf(window(0), window(250), window(500)))
        assertEquals(900, stitched.height)
        assertTrue(stitched.pixels.contentEquals(tall.pixels))
    }

    // A screen that didn't scroll (end of the page) adds nothing.
    @Test
    fun stitchStopsAtTheEnd() {
        val screen = Rgb(50, 400, IntArray(50 * 400) { it / 50 * 997 })
        assertEquals(400, stitch(listOf(screen, screen)).height)
    }
}
