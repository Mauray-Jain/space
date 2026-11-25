package space.app.understand

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Opens the MiniLM understander from the APK's assets. Callers close it when the capture is done (CLAUDE.md). */
@Singleton
class Understanding @Inject constructor(@ApplicationContext private val context: Context) {
    fun open(): Understander = Understander(
        context.assets.open("intent-minilm.onnx").use { it.readBytes() },
        context.assets.open("intent-vocab.txt").use { it.readBytes() }.decodeToString().lines(),
    )
}
