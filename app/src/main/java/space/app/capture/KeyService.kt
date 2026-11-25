package space.app.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import space.app.MainActivity
import space.app.Settings
import space.app.understand.links
import space.app.vision.stitch

/**
 * The Essential Key (Nothing's defaults, PLAN.md §1): press = capture the screen, hold = speak,
 * double-press = open Space. It listens for that one key; the screen is read only on a press.
 */
@AndroidEntryPoint
class KeyService : AccessibilityService() {
    @Inject lateinit var settings: Settings
    @Inject lateinit var screenshots: Screenshots

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var downAt = 0L
    private var lastUpAt = 0L
    private var held = false
    private val onHold = Runnable { held = true; openSheet(voice = true) }
    private val onPress = Runnable { captureScreen() }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; scope.cancel(); super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (settings.learningKey && event.action == KeyEvent.ACTION_UP && event.keyCode !in ignoredWhenLearning) {
            settings.keyCode = event.keyCode
            settings.learningKey = false
            return true
        }
        if (!settings.keyEnabled || event.keyCode != settings.keyCode) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                downAt = event.eventTime
                held = false
                handler.postDelayed(onHold, HOLD_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(onHold)
                if (held) return true
                if (event.eventTime - lastUpAt < DOUBLE_MS) {
                    handler.removeCallbacks(onPress)
                    lastUpAt = 0
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    lastUpAt = event.eventTime
                    handler.postDelayed(onPress, DOUBLE_MS)
                }
            }
        }
        return true
    }

    private fun captureScreen() {
        val app = rootInActiveWindow?.packageName?.toString()
        val url = currentUrl()
        scope.launch {
            val shot = screenshot()
            if (shot == null) {
                // EDGE_CASES.md §4: Android blocks captures of secure screens (banking, video).
                openSheet(problem = "This app doesn't allow screenshots. Save a note or speak instead.", app = app)
                return@launch
            }
            val file = withContext(Dispatchers.IO) { screenshots.newPendingFile().also { f -> f.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
            shot.recycle()
            openSheet(screenshot = file.path, url = url, app = app)
        }
    }

    /**
     * Long screenshot: scrolls down and screenshots until the page stops moving (at most
     * [MAX_SCREENS]), then joins the screens. Started from the sheet's "Longer" button.
     */
    fun captureLonger(first: String, url: String?, app: String?) = scope.launch {
        delay(400) // let the sheet close
        val screens = mutableListOf(android.graphics.BitmapFactory.decodeFile(first).toRgb())
        repeat(MAX_SCREENS - 1) {
            scrollDown()
            delay(700)
            val next = screenshot()?.let { b -> b.toRgb().also { b.recycle() } } ?: return@repeat
            if (next.pixels.contentEquals(screens.last().pixels)) return@repeat
            screens += next
        }
        val file = withContext(Dispatchers.Default) {
            val joined = stitch(screens).toBitmap()
            screenshots.newPendingFile().also { f -> f.outputStream().use { joined.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        java.io.File(first).delete()
        openSheet(screenshot = file.path, url = url, app = app)
    }

    private suspend fun screenshot(): Bitmap? = suspendCancellableCoroutine { cont ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                val copy = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                hardware?.recycle()
                result.hardwareBuffer.close()
                cont.resume(copy)
            }
            override fun onFailure(errorCode: Int) = cont.resume(null)
        })
    }

    private suspend fun scrollDown() = suspendCancellableCoroutine { cont ->
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val path = Path().apply { moveTo(x, metrics.heightPixels * 0.75f); lineTo(x, metrics.heightPixels * 0.30f) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 400)).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) = cont.resume(Unit)
            override fun onCancelled(g: GestureDescription?) = cont.resume(Unit)
        }, null)
    }

    /** The page's address when the screen is a browser (wishlist #7). */
    private fun currentUrl(): String? {
        val root = rootInActiveWindow ?: return null
        for (id in addressBars) {
            val text = root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.text?.toString() ?: continue
            return links(text).firstOrNull() ?: continue
        }
        return null
    }

    private fun openSheet(voice: Boolean = false, screenshot: String? = null, url: String? = null, app: String? = null, problem: String? = null) {
        startActivity(
            Intent(this, QuickEntryActivity::class.java)
                .putExtra(QuickEntryActivity.EXTRA_VOICE, voice)
                .putExtra(QuickEntryActivity.EXTRA_SCREENSHOT, screenshot)
                .putExtra(QuickEntryActivity.EXTRA_URL, url)
                .putExtra(QuickEntryActivity.EXTRA_APP, app)
                .putExtra(QuickEntryActivity.EXTRA_PROBLEM, problem)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    companion object {
        /** Set while the service runs, so the sheet can ask it for a longer screenshot. */
        @Volatile var instance: KeyService? = null
            private set

        private const val HOLD_MS = 500L
        private const val DOUBLE_MS = 300L
        private const val MAX_SCREENS = 5
        private val ignoredWhenLearning = setOf(
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
        )
        private val addressBars = listOf(
            "com.android.chrome:id/url_bar",
            "com.brave.browser:id/url_bar",
            "com.microsoft.emmx:id/url_bar",
            "com.vivaldi.browser:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.duckduckgo.mobile.android:id/omnibarTextInput",
        )
    }
}
