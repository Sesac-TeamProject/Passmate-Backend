package kr.passmate.admin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.hostlevel.dto.GradeEvaluationResult
import kr.passmate.hostlevel.service.HostGradeService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 등급 판정 수동 실행 (SC-013).
 *
 * 평상시 승급은 세션 종료 때 자동으로 반영된다. 이 API 는 30일 유지 판정을 앞당기거나,
 * 집계가 틀어진 계정을 다시 맞출 때 쓰는 운영용 손잡이다.
 *
 * admin 은 자체 로직을 갖지 않는다 — hostlevel 의 Service 를 부르기만 한다.
 */
@Tag(name = "관리자 콘솔")
@RestController
@RequestMapping("/admin/grades")
class AdminGradeController(
    private val hostGradeService: HostGradeService,
) {

    @Operation(
        summary = "등급 판정 실행",
        description = "userId 를 주면 그 회원만, 없으면 세션을 진행한 적 있는 호스트 전체를 판정한다.",
    )
    @PostMapping("/evaluate")
    fun evaluate(@RequestParam(required = false) userId: Long?): GradeEvaluationResult =
        userId?.let { hostGradeService.evaluateOne(it) } ?: hostGradeService.evaluateAll()
}
