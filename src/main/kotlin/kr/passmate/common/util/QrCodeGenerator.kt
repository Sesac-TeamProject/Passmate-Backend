package kr.passmate.common.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** QR 코드 PNG 생성. 외부 서비스를 쓰지 않고 서버에서 만든다. */
@Component
class QrCodeGenerator {

    fun toPngBytes(content: String, size: Int = DEFAULT_SIZE): ByteArray {
        val matrix = try {
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
                ),
            )
        } catch (e: Exception) {
            throw BusinessException(ErrorCode.INTERNAL_ERROR, "QR 코드 생성에 실패했습니다.", e)
        }

        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until size) {
            for (y in 0 until size) {
                image.setRGB(x, y, if (matrix[x, y]) BLACK else WHITE)
            }
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "PNG", out)
            out.toByteArray()
        }
    }

    companion object {
        private const val DEFAULT_SIZE = 320
        private const val BLACK = 0x000000
        private const val WHITE = 0xFFFFFF
    }
}
