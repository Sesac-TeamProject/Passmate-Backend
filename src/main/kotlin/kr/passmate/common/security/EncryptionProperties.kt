package kr.passmate.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 저장할 때 암호화해야 하는 값(정산 계좌번호)에 쓰는 키.
 *
 * 비어 있으면 **조용히 평문으로 저장하지 않고** 그 경로를 막는다 —
 * 설정이 빠진 채로 계좌번호가 평문으로 쌓이면 되돌릴 방법이 없다.
 */
@ConfigurationProperties(prefix = "passmate.encryption")
data class EncryptionProperties(
    /** 32바이트(AES-256) 이상의 임의 문자열. 운영은 env 로 주입한다 */
    val key: String,
) {
    val isConfigured: Boolean get() = key.length >= MIN_KEY_LENGTH

    companion object {
        const val MIN_KEY_LENGTH = 32
    }
}
