package kr.passmate.auth.client

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.Date

/**
 * Apple 토큰 API(/auth/token·/auth/revoke)용 client secret 을 만든다.
 * Apple 은 고정 시크릿 대신 .p8 개인키로 서명한 ES256 JWT 를 요구한다 — iss=팀 ID, sub=클라이언트 ID(번들 ID).
 * 요청 때마다 짧은 수명(5분)으로 새로 만든다. 오래 쓰는 시크릿을 들고 있을 이유가 없다.
 */
class AppleClientSecretFactory(
    private val properties: AppleProperties,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val privateKey: ECPrivateKey by lazy { parsePrivateKey(properties.privateKey) }

    fun create(clientId: String): String {
        val now = clock.instant()
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(properties.keyId)
            .type(JOSEObjectType.JWT)
            .build()
        val claims = JWTClaimsSet.Builder()
            .issuer(properties.teamId)
            .subject(clientId)
            .audience(properties.issuer)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(LIFETIME)))
            .build()
        return SignedJWT(header, claims).apply { sign(ECDSASigner(privateKey)) }.serialize()
    }

    companion object {
        private val LIFETIME: Duration = Duration.ofMinutes(5)

        /** .p8(PEM) → EC 개인키. BEGIN/END 줄·공백·줄바꿈·`\n` 글자를 걷어 내고 본문 base64 만 읽는다 */
        fun parsePrivateKey(pem: String): ECPrivateKey {
            val body = pem
                .replace("\\n", "")
                .replace(Regex("-----(BEGIN|END) [A-Z ]+-----"), "")
                .replace(Regex("\\s"), "")
            val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(body))
            return KeyFactory.getInstance("EC").generatePrivate(spec) as ECPrivateKey
        }
    }
}
