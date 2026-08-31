package kr.passmate.common.config

@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "passmate.client")
data class ClientProperties(
    /** 웹 클라이언트 주소. QR 이 가리킬 입장 링크를 만들 때 쓴다. */
    val baseUrl: String,
) {
    fun joinUrl(pin: String): String = "${baseUrl.trimEnd('/')}/join?pin=$pin"
}
