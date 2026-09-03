package kr.passmate.common.security

import kr.passmate.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 저장용 암호화 (AES-256-GCM).
 *
 * 계좌번호가 지나는 길이라 "돌려받을 수 있다"만으로는 부족하다 —
 * 같은 값이 늘 같은 암호문이 되면 원문을 몰라도 "이 둘은 같은 계좌"가 새어 나간다.
 */
class TextEncryptorTest {

    private val encryptor = TextEncryptor(EncryptionProperties("test-encryption-key-for-unit-tests-32bytes"))

    @Test
    fun `암호화한 값을 복호화하면 원문이 나온다`() {
        val encrypted = encryptor.encrypt("110123456789")

        assertThat(encryptor.decrypt(encrypted)).isEqualTo("110123456789")
    }

    @Test
    fun `암호문에는 원문이 남지 않는다`() {
        assertThat(encryptor.encrypt("110123456789")).doesNotContain("110123456789")
    }

    @Test
    fun `같은 값을 두 번 암호화해도 암호문은 다르다`() {
        val first = encryptor.encrypt("110123456789")
        val second = encryptor.encrypt("110123456789")

        // 같으면 원문을 몰라도 "이 둘은 같은 계좌"라는 사실이 드러난다
        assertThat(first).isNotEqualTo(second)
        assertThat(encryptor.decrypt(first)).isEqualTo(encryptor.decrypt(second))
    }

    @Test
    fun `한글도 그대로 돌아온다`() {
        assertThat(encryptor.decrypt(encryptor.encrypt("전혜림"))).isEqualTo("전혜림")
    }

    @Test
    fun `키가 없으면 조용히 평문으로 저장하지 않고 막는다`() {
        val unconfigured = TextEncryptor(EncryptionProperties(""))

        assertThatThrownBy { unconfigured.encrypt("110123456789") }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `키가 짧으면 설정되지 않은 것으로 본다`() {
        val tooShort = TextEncryptor(EncryptionProperties("short-key"))

        assertThatThrownBy { tooShort.encrypt("110123456789") }
            .isInstanceOf(BusinessException::class.java)
    }
}
