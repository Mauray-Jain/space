package space.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.app.data.ItemRepository
import space.app.remind.Reminders
import space.app.ui.Capture
import space.app.ui.formatWhen
import space.app.understand.Filer
import space.app.understand.Understood
import space.app.vision.SCREENSHOT_TITLE
import space.app.voice.VoiceNotes

/** A screen capture waiting in the sheet. [crop] is in fractions of the image (left, top, right, bottom). */
data class PendingShot(val path: String, val url: String?, val app: String?, val crop: List<Float>? = null)

@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    private val repo: ItemRepository,
    private val filer: Filer,
    private val reminders: Reminders,
    private val understanding: space.app.understand.Understanding,
    recorder: Recorder,
    notes: VoiceNotes,
) : ViewModel() {
    val voice = VoiceInput(viewModelScope, recorder, notes)

    private val _shot = MutableStateFlow<PendingShot?>(null)
    val shot: StateFlow<PendingShot?> = _shot.asStateFlow()

    fun setShot(shot: PendingShot?) { _shot.value = shot }
    fun crop(fractions: List<Float>?) { _shot.value = _shot.value?.copy(crop = fractions) }

    /** "Tap to skip the screenshot" (PLAN.md §1): the capture becomes text or voice only. */
    fun removeShot() {
        _shot.value?.let { File(it.path).delete() }
        _shot.value = null
    }

    // Loaded while the capture sheet is open, closed when it's done (CLAUDE.md: models stay light).
    private var understander: space.app.understand.Understander? = null

    fun understand(text: String): Understood? =
        (understander ?: understanding.open().also { understander = it }).understand(text, LocalDateTime.now()).firstOrNull()

    fun releaseModel() {
        understander?.close()
        understander = null
    }

    override fun onCleared() = releaseModel()

    // The activity finishes right after Save, so the write must outlive viewModelScope.
    fun save(c: Capture) {
        val audio = voice.takeAudio()
        val shot = _shot.value
        _shot.value = null
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val image = shot?.let { cropped(it) }
            // A bare screenshot is named "Screenshot" until it's read; then the rules take over.
            val blank = c.text.isBlank()
            val ids = filer.file(
                c.type, if (blank && image != null) SCREENSHOT_TITLE else c.text, c.remindAt, c.understood.takeUnless { blank },
                audio = audio, screenshot = image, source = shot?.url, app = shot?.app,
            )
            // Saved from outside the app: show what a reminder became, with Undo and Edit.
            ids.forEach { id ->
                val item = repo.get(id) ?: return@forEach
                item.remindAt?.let { reminders.showCreated(item, "Reminder set · ${formatWhen(it)}") }
            }
        }
    }

    /** Leaving without saving: nothing stays behind. */
    fun discard() {
        voice.reset()
        removeShot()
    }

    private fun cropped(shot: PendingShot): File {
        val file = File(shot.path)
        val c = shot.crop ?: return file
        val full = BitmapFactory.decodeFile(file.path) ?: return file
        val r = Rect((c[0] * full.width).toInt(), (c[1] * full.height).toInt(), (c[2] * full.width).toInt(), (c[3] * full.height).toInt())
        if (r.width() < 8 || r.height() < 8) return file
        val part = Bitmap.createBitmap(full, r.left, r.top, r.width(), r.height())
        file.outputStream().use { part.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
