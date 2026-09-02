package kr.passmate.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 파일 저장소(S3) 설정.
 *
 * **자격증명은 여기 두지 않는다.** 운영은 EC2 인스턴스 역할, 로컬은 AWS SDK 기본 체인이
 * 알아서 찾는다 — 키를 설정으로 받으면 그 값이 .env·이미지·로그에 흘러 다닌다.
 */
@ConfigurationProperties(prefix = "passmate.storage")
data class StorageProperties(
    val bucket: String,
    val region: String,
    /** 프리사인 URL 유효 시간(분). 짧을수록 새어도 덜 위험하다 */
    val presignedMinutes: Long,
    /** 업로드 허용 크기 상한(MB). PTT 클립은 몇 초짜리라 크게 잡을 이유가 없다 */
    val maxUploadMb: Long,
) {
    val presignedTtl: Duration get() = Duration.ofMinutes(presignedMinutes)
    val maxUploadBytes: Long get() = maxUploadMb * 1024 * 1024
}
