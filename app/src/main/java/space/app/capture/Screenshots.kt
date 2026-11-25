package space.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.app.data.ItemRepository
import space.app.data.ItemType
import space.app.remind.Reminders
import space.app.ui.formatWhen
import space.app.understand.OcrLine
import space.app.vision.Ocr
import space.app.vision.Rgb
import space.app.vision.codes
import space.app.vision.fileScreenshot

/** Screen captures: stored in the item's own folder (never the Gallery), read by one short job each. */
@Singleton
class Screenshots @Inject constructor(@ApplicationContext private val context: Context) {
    /** Captures waiting in the sheet live here until saved or dismissed. */
    val pending = File(context.cacheDir, "captures")

    fun newPendingFile(): File {
        // Captures left behind (the sheet was killed) don't linger past a day.
        pending.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        return File(pending.apply { mkdirs() }, "${System.currentTimeMillis()}.png")
    }

    /** One short WorkManager job per capture, so it's read right away, not when the app is next opened (PLAN.md §4). */
    fun processLater(itemId: Long, app: String?) {
        val request = OneTimeWorkRequestBuilder<ReadScreenshotWorker>()
            .setInputData(workDataOf(ITEM to itemId, APP to app))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("read-$itemId", ExistingWorkPolicy.REPLACE, request)
    }

    /** OCR lines and QR codes. The OCR models are loaded for this one image and closed right after. */
    suspend fun read(file: File): Pair<List<OcrLine>, List<String>> = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return@withContext emptyList<OcrLine>() to emptyList()
        val rgb = bitmap.toRgb()
        bitmap.recycle()
        fun asset(name: String) = context.assets.open(name).use { it.readBytes() }
        val lines = Ocr(asset("ocr-det.onnx"), asset("ocr-rec.onnx"), asset("ocr-dict.txt").decodeToString().lines()).use { it.read(rgb) }
        lines to codes(rgb)
    }

    companion object {
        const val FILE = "screenshot.png"
        const val ITEM = "item"
        const val APP = "app"
    }
}

fun Bitmap.toRgb(): Rgb {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return Rgb(width, height, pixels)
}

fun Rgb.toBitmap(): Bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerDeps {
    fun repo(): ItemRepository
    fun screenshots(): Screenshots
    fun reminders(): Reminders
}

/** Reads one capture's screenshot and files it (fileScreenshot). Short; no model stays loaded. */
class ReadScreenshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerDeps::class.java)
        val id = inputData.getLong(Screenshots.ITEM, -1)
        val item = deps.repo().get(id) ?: return Result.success() // deleted meanwhile
        val file = File(deps.repo().filesDir(id), Screenshots.FILE)
        if (!file.exists()) return Result.success()
        val (lines, codes) = deps.screenshots().read(file)
        val filed = fileScreenshot(item, lines, codes, inputData.getString(Screenshots.APP), LocalDateTime.now())
        if (deps.repo().get(id) == null) return Result.success()
        deps.repo().update(filed)
        // Automatic by default, with Undo/Edit (PLAN.md §1).
        if (filed.type == ItemType.REMINDER && item.type != ItemType.REMINDER) {
            deps.reminders().showCreated(filed, "Reminder set · ${filed.remindAt?.let(::formatWhen)}")
        }
        return Result.success()
    }
}
