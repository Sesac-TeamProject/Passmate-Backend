package kr.passmate.coin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.coin.service.PortOneWebhookService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * 포트원 웹훅 수신 — 화면이 없는 서버 간 호출.
 *
 * 인증 없이 열려 있다(포트원이 우리 토큰을 가질 리 없다). **서명이 유일한 방벽**이므로
 * 본문을 건드리기 전에 먼저 검증한다.
 */
@Tag(name = "유료 방·결제")
@RestController
class PortOneWebhookController(
    private val portOneWebhookService: PortOneWebhookService,
) {

    /**
     * 본문을 문자열 그대로 받는다 — 서명은 **바이트 그대로**에 대해 계산된 값이라,
     * 객체로 역직렬화한 뒤 다시 문자열로 만들면 공백·필드 순서가 달라져 검증이 깨진다.
     */
    @Operation(
        summary = "포트원 웹훅",
        description = "결제 완료·취소·실패 수신. 서명 검증 후 충전 건 상태를 맞춘다. 멱등.",
    )
    @PostMapping("/webhooks/portone")
    fun receive(
        @RequestBody body: String,
        @RequestHeader headers: Map<String, String>,
    ) {
        portOneWebhookService.verifySignature(body, headers)
        // 여기서부터는 예외를 밖으로 내보내지 않는다 — 200 이 아니면 포트원이 재시도한다
        portOneWebhookService.handle(body)
    }
}
