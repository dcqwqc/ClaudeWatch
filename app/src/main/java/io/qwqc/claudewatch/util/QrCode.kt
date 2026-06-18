package io.qwqc.claudewatch.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes [content] as a QR code at its native module resolution (one pixel per
 * module, plus a small quiet zone). Render it with [androidx.compose.ui.graphics.FilterQuality.None]
 * so it stays crisp and scannable when scaled up to fill the watch face.
 *
 * Returns null if the string can't be encoded (it never should for an SSH key).
 */
fun qrImageBitmap(content: String): ImageBitmap? = runCatching {
    val hints = mapOf(
        // Smaller-than-default quiet zone — every module counts on a watch.
        EncodeHintType.MARGIN to 2,
        // Medium error correction survives glare/low-res phone capture.
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    // width/height 0 → ZXing sizes to the smallest grid that fits the data.
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h) { i ->
        if (matrix.get(i % w, i / w)) Color.BLACK else Color.WHITE
    }
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        .apply { setPixels(pixels, 0, w, 0, 0, w, h) }
        .asImageBitmap()
}.getOrNull()
