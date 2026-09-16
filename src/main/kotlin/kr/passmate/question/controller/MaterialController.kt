package kr.passmate.question.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.question.dto.MaterialExtractResponse
import kr.passmate.question.service.MaterialExtractionService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 강의자료 본문 추출. AI 문항 생성의 입력을 만드는 자리라 question 기능이 갖는다.
 *
 * 파일을 보관하지 않으므로 방·세트에 매이지 않는다 — 올리면 글자만 돌려주는 한 번짜리 호출이다.
 */
@Tag(name = "AI 문제 생성")
@RestController
class MaterialController(
    private val materialExtractionService: MaterialExtractionService,
) {

    @Operation(
        summary = "강의자료 본문 추출",
        description = "PDF · 워드(docx) · 파워포인트(pptx) · 텍스트 파일에서 글자만 뽑아 돌려준다. " +
            "그 값을 AI 생성 요청의 material 에 넣으면 된다. 파일은 저장하지 않는다. 회원 전용.",
    )
    @PostMapping("/materials/extract", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun extract(
        @CurrentUser principal: UserPrincipal,
        @RequestPart("file") file: MultipartFile,
    ): MaterialExtractResponse = materialExtractionService.extract(file)
}
