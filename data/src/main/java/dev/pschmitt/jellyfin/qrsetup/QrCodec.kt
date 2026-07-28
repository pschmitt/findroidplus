package dev.pschmitt.jellyfin.qrsetup

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.NotFoundException
import com.google.zxing.common.BitMatrix

/**
 * Pure ZXing encode/decode, deliberately free of any Android graphics/camera type
 * (`android.graphics.Bitmap`, CameraX `ImageProxy`, ...) so it's unit-testable on a plain JVM -
 * callers in `app/phone` convert to/from `Bitmap`/`ImageProxy` around this.
 */
object QrCodec {
    private const val DEFAULT_SIZE = 512

    fun encode(payload: String, size: Int = DEFAULT_SIZE): BitMatrix {
        return MultiFormatWriter()
            .encode(
                payload,
                BarcodeFormat.QR_CODE,
                size,
                size,
                mapOf(EncodeHintType.MARGIN to 1),
            )
    }

    /** Returns null if [binaryBitmap] doesn't contain a decodable QR code. */
    fun decode(binaryBitmap: BinaryBitmap): String? {
        val reader =
            MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
            }
        return try {
            reader.decode(binaryBitmap).text
        } catch (e: NotFoundException) {
            null
        }
    }
}
