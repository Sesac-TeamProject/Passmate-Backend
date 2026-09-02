package kr.passmate.common.storage

/**
 * 파일 저장소. 구현체가 S3 SDK 를 감추고, Service 는 키와 바이트만 넘긴다.
 * 테스트는 Fake 로 갈아끼운다 — 자동화 테스트가 실제 버킷을 건드리지 않는다.
 */
interface StorageClient {

    /** 업로드. 실패는 [StorageException] 으로 던진다. */
    fun upload(key: String, contentType: String, bytes: ByteArray)

    /**
     * 잠깐 열리는 다운로드 링크.
     *
     * 버킷은 비공개로 두고 필요할 때만 서명한 URL 을 내보낸다 —
     * 퍼블릭 버킷이면 힌트 클립 주소 하나가 새는 순간 누구나 듣는다.
     * 만료되므로 **저장하지 않고 응답할 때마다 새로 만든다.**
     */
    fun presignedUrl(key: String): String
}

/** 저장소 호출 실패. Service 가 잡아 502 로 번역한다. */
class StorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
