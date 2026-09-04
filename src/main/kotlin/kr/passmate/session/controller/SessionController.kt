package kr.passmate.session.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.session.dto.AnswerResponse
import kr.passmate.session.dto.AnswerSubmitRequest
import kr.passmate.session.dto.QuestionResultResponse
import kr.passmate.session.dto.RankingEntry
import kr.passmate.session.dto.ScreenLockRequest
import kr.passmate.session.dto.ScreenLockResponse
import kr.passmate.session.dto.SessionSnapshotResponse
import kr.passmate.session.dto.SubmissionStatusPayload
import kr.passmate.session.service.AnswerService
import kr.passmate.session.service.SessionQueryService
import kr.passmate.session.service.SessionService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 세션 제어·조회.
 *
 * **제어는 전부 여기(REST)로 들어온다.** WebSocket 은 결과를 내보내기만 하는 단방향 채널이라
 * 호스트 검증이 이 한 곳에만 있으면 된다.
 */
@Tag(name = "실시간 세션 진행")
@RestController
@RequestMapping("/rooms/{roomId}/session")
class SessionController(
    private val sessionService: SessionService,
    private val sessionQueryService: SessionQueryService,
    private val answerService: AnswerService,
) {

    @Operation(summary = "세션 시작", description = "확정 세트의 문항을 복사해 두고 1번 문항을 연다. 호스트만.")
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun start(@CurrentUser principal: UserPrincipal, @PathVariable roomId: Long) =
        sessionService.start(roomId, principal.userId)

    @Operation(summary = "다음 문항 시작", description = "열려 있는 문항을 먼저 마감하고 다음을 연다. 호스트만.")
    @PostMapping("/next")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun next(@CurrentUser principal: UserPrincipal, @PathVariable roomId: Long) =
        sessionService.next(roomId, principal.userId)

    @Operation(summary = "현재 문항 마감", description = "제한시간 전에 바로 마감한다. 호스트만.")
    @PostMapping("/current/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun endCurrent(@CurrentUser principal: UserPrincipal, @PathVariable roomId: Long) =
        sessionService.endCurrentQuestion(roomId, principal.userId)

    @Operation(summary = "세션 종료", description = "열려 있던 문항도 함께 마감하고 방을 닫는다. 호스트만.")
    @PostMapping("/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun end(@CurrentUser principal: UserPrincipal, @PathVariable roomId: Long) =
        sessionService.end(roomId, principal.userId)

    @Operation(
        summary = "학생 화면 잠금/해제",
        description = "잠금 중에는 답안 제출이 막히고 전 참가자에게 SCREEN_LOCKED 가 나간다. 호스트만.",
    )
    @PutMapping("/lock")
    fun lock(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: ScreenLockRequest,
    ): ScreenLockResponse {
        val room = sessionService.lockScreen(roomId, principal.userId, request.locked)
        return ScreenLockResponse(roomId = room.id, screenLocked = room.screenLocked)
    }

    @Operation(
        summary = "세션 상태 조회",
        description = "재접속 복구용 스냅샷. 진행 중 문항의 정답은 포함하지 않는다.",
    )
    @GetMapping
    fun snapshot(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
    ): SessionSnapshotResponse = sessionQueryService.snapshot(roomId, principal)

    @Operation(summary = "제출 현황 조회", description = "실시간 제출 수·정답률·응답 분포. 호스트만.")
    @GetMapping("/current/submissions")
    fun submissions(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
    ): SubmissionStatusPayload = sessionQueryService.submissionStatus(roomId, principal.userId)

    @Operation(summary = "답안 제출", description = "제출 시각은 서버 시계로만 잰다. 문항당 한 번만 낼 수 있다.")
    @PostMapping("/questions/{questionId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
        @PathVariable questionId: Long,
        @Valid @RequestBody request: AnswerSubmitRequest,
    ): AnswerResponse =
        AnswerResponse.from(answerService.submit(roomId, principal, questionId, request.submitted))

    @Operation(summary = "문항 결과 조회", description = "마감된 문항만. 정답·응답 분포·정답률과 랭킹.")
    @GetMapping("/questions/{questionId}/result")
    fun questionResult(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
        @PathVariable questionId: Long,
    ): QuestionResultResponse = sessionQueryService.questionResult(roomId, questionId, principal)

    @Operation(summary = "랭킹 조회", description = "누적 점수 기준. 동점은 같은 등수로 묶는다.")
    @GetMapping("/ranking")
    fun ranking(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
    ): List<RankingEntry> = sessionQueryService.ranking(roomId, principal)
}
