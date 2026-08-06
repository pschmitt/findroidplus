package dev.pschmitt.jellyfin.qrsetup

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Adapts a [BitMatrix] straight back into a [LuminanceSource], skipping any real camera/bitmap
 * plumbing - lets [QrCodec.decode] be exercised on a plain JVM against exactly what
 * [QrCodec.encode] just produced.
 */
private class BitMatrixLuminanceSource(private val matrix: BitMatrix) :
    LuminanceSource(matrix.width, matrix.height) {
    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val out = if (row != null && row.size >= width) row else ByteArray(width)
        for (x in 0 until width) out[x] = if (matrix.get(x, y)) 0 else 0xFF.toByte()
        return out
    }

    override fun getMatrix(): ByteArray {
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) out[y * width + x] = if (matrix.get(x, y)) 0 else 0xFF.toByte()
        }
        return out
    }
}

class QrCodecTest {

    private fun toBinaryBitmap(matrix: BitMatrix) =
        BinaryBitmap(HybridBinarizer(BitMatrixLuminanceSource(matrix)))

    @Test
    fun `encoded payload decodes back to the same text`() {
        val payload = "jollyfin-qr-setup:" + "x".repeat(200)
        val matrix = QrCodec.encode(payload)
        val decoded = QrCodec.decode(toBinaryBitmap(matrix))
        assertEquals(payload, decoded)
    }

    @Test
    fun `decoding a blank bitmap returns null instead of throwing`() {
        val blank = BitMatrix(64, 64)
        val decoded = QrCodec.decode(toBinaryBitmap(blank))
        assertNull(decoded)
    }
}
