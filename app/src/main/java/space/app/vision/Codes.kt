package space.app.vision

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader

/** QR codes and barcodes in an image (ZXing), e.g. a menu or ticket link. */
fun codes(image: Rgb): List<String> {
    val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, image.pixels)))
    return try {
        GenericMultipleBarcodeReader(MultiFormatReader())
            .decodeMultiple(bitmap, mapOf(DecodeHintType.TRY_HARDER to true))
            .map { it.text }.distinct()
    } catch (e: com.google.zxing.NotFoundException) {
        emptyList()
    }
}
