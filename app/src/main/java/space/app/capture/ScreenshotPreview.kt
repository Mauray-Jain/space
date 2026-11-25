package space.app.capture

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** The captured screen above the text: drag on it to crop, remove it, or make it longer. */
@Composable
fun ScreenshotPreview(shot: PendingShot, onCrop: (List<Float>?) -> Unit, onRemove: () -> Unit, onLonger: (() -> Unit)?) {
    val bitmap = remember(shot.path) {
        BitmapFactory.decodeFile(shot.path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
    } ?: return
    var size by remember { mutableStateOf(IntSize.Zero) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            Image(
                bitmap = bitmap,
                contentDescription = "Screenshot",
                modifier = Modifier
                    .heightIn(max = 260.dp)
                    .onSizeChanged { size = it }
                    .pointerInput(shot.path) {
                        detectDragGestures(
                            onDragStart = { start = it; end = it },
                            onDrag = { change, _ -> end = change.position },
                            onDragEnd = {
                                val a = start; val b = end
                                if (a != null && b != null && size.width > 0) {
                                    fun fx(v: Float) = (v / size.width).coerceIn(0f, 1f)
                                    fun fy(v: Float) = (v / size.height).coerceIn(0f, 1f)
                                    onCrop(listOf(fx(minOf(a.x, b.x)), fy(minOf(a.y, b.y)), fx(maxOf(a.x, b.x)), fy(maxOf(a.y, b.y))))
                                }
                            },
                        )
                    },
            )
            val a = start; val b = end
            if (shot.crop != null && a != null && b != null) {
                with(density) {
                    Box(
                        Modifier
                            .offset(minOf(a.x, b.x).toDp(), minOf(a.y, b.y).toDp())
                            .size(kotlin.math.abs(b.x - a.x).toDp(), kotlin.math.abs(b.y - a.y).toDp())
                            .border(2.dp, MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRemove) { Text("Remove") }
            if (shot.crop != null) TextButton(onClick = { start = null; end = null; onCrop(null) }) { Text("Undo crop") }
            else Text("Drag on it to crop", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
            if (onLonger != null) TextButton(onClick = onLonger) { Text("Longer") }
        }
    }
}
