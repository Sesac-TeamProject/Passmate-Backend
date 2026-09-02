package kr.passmate.common.storage

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

/**
 * S3 구현체.
 *
 * 자격증명을 코드나 설정으로 받지 않는다 — SDK 기본 제공자 체인이 찾는다.
 * 운영에서는 EC2 인스턴스 역할이 잡히므로 서버에 키 파일이 남지 않는다.
 */
@Component
class S3StorageClient(
    private val properties: StorageProperties,
) : StorageClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val region: Region = Region.of(properties.region)

    private val s3: S3Client by lazy { S3Client.builder().region(region).build() }

    private val presigner: S3Presigner by lazy { S3Presigner.builder().region(region).build() }

    override fun upload(key: String, contentType: String, bytes: ByteArray) {
        try {
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(key)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromBytes(bytes),
            )
        } catch (e: Exception) {
            // 키·버킷 이름이 메시지에 섞일 수 있어 원문은 로그에만 남긴다
            log.warn("S3 업로드 실패 key={}", key, e)
            throw StorageException("파일을 저장하지 못했습니다.", e)
        }
    }

    override fun presignedUrl(key: String): String = try {
        presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignedTtl)
                .getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket).key(key).build())
                .build(),
        ).url().toString()
    } catch (e: Exception) {
        log.warn("프리사인 URL 생성 실패 key={}", key, e)
        throw StorageException("파일 주소를 만들지 못했습니다.", e)
    }
}
