package kr.passmate.common.security

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 저장용 대칭 암호화 (AES-256-GCM).
 *
 * 계좌번호처럼 **다시 읽어야 하지만 평문으로 두면 안 되는** 값에 쓴다.
 * 비밀번호였다면 해시를 썼겠지만, 정산하려면 원문을 복구할 수 있어야 한다.
 *
 * IV 는 매번 새로 뽑아 앞에 붙인다 — 같은 계좌번호가 늘 같은 암호문이 되면
 * 원문을 몰라도 "이 둘은 같은 계좌"라는 사실이 새어 나간다.
 */
@Component
class TextEncryptor(
    private val properties: EncryptionProperties,
) {

    fun encrypt(plain: String): String {
        val cipher = cipher(Cipher.ENCRYPT_MODE, randomIv())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray())
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(encoded: String): String {
        val raw = Base64.getDecoder().decode(encoded)
        val iv = raw.copyOfRange(0, IV_LENGTH)
        val body = raw.copyOfRange(IV_LENGTH, raw.size)
        return String(cipher(Cipher.DECRYPT_MODE, iv).doFinal(body))
    }

    private fun cipher(mode: Int, iv: ByteArray): Cipher {
        if (!properties.isConfigured) {
            // 키가 없는데 평문으로 저장하면 되돌릴 방법이 없다. 조용히 넘어가지 않는다
            throw BusinessException(
                ErrorCode.INTERNAL_ERROR,
                "암호화 키가 설정되지 않아 처리할 수 없습니다.",
            )
        }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(keyBytes(), "AES"), GCMParameterSpec(TAG_BITS, iv))
        }
    }

    /** 키 길이를 AES-256 에 맞추려고 SHA-256 으로 한 번 접는다. */
    private fun keyBytes(): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(properties.key.toByteArray())

    private fun randomIv() = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
