package kr.passmate.support

import kr.passmate.common.storage.StorageClient
import kr.passmate.common.storage.StorageException
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 테스트용 저장소. **네트워크를 타지 않으므로 실제 버킷을 건드리지 않는다.**
 */
class FakeStorageClient : StorageClient {

    /** 올라간 파일. 키 → 바이트 */
    val uploaded = linkedMapOf<String, ByteArray>()

    /** 마지막으로 받은 Content-Type. 확장자·타입이 제대로 넘어가는지 볼 때 쓴다 */
    var lastContentType: String? = null
        private set

    /** true 면 업로드가 실패한다. 저장소 장애 경로를 확인할 때 켠다 */
    var failOnUpload: Boolean = false

    override fun upload(key: String, contentType: String, bytes: ByteArray) {
        if (failOnUpload) throw StorageException("테스트용 저장 실패")
        lastContentType = contentType
        uploaded[key] = bytes
    }

    override fun presignedUrl(key: String): String = "$BASE_URL/$key?signature=fake"

    fun reset() {
        uploaded.clear()
        lastContentType = null
        failOnUpload = false
    }

    companion object {
        const val BASE_URL = "https://fake-storage.test"
    }
}

/**
 * S3 를 Fake 로 갈아끼운다. 통합 테스트에서 `@Import(FakeStorageConfig::class)` 로 쓴다.
 */
@TestConfiguration
class FakeStorageConfig {

    @Bean
    @Primary
    fun fakeStorageClient(): StorageClient = FakeStorageClient()
}
