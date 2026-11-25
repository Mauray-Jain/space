package space.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import space.app.capture.Screenshots

// OCR from the APK's assets on Android's ONNX Runtime (milestone 6).
@RunWith(AndroidJUnit4::class)
class ScreenshotReadTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun readsAFlyer() = runBlocking {
        val bitmap = Bitmap.createBitmap(720, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textSize = 48f }
            drawText("SUMMER JAZZ NIGHT", 40f, 80f, paint)
            paint.typeface = Typeface.DEFAULT; paint.textSize = 32f
            drawText("Saturday 28 November", 40f, 160f, paint)
        }
        val file = File(context.cacheDir, "flyer.png").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val (lines, codes) = Screenshots(context).read(file)
        assertEquals(listOf("SUMMER JAZZ NIGHT", "Saturday 28 November"), lines.map { it.text })
        assertEquals(emptyList<String>(), codes)
    }
}
