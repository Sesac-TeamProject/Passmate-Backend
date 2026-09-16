package kr.passmate.question.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 강의자료 파일에서 뽑은 본문. 이 [text] 를 그대로 [AiGenerateRequest.material] 에 넣으면 된다.
 * 파일은 서버에 남지 않으므로 다시 받을 주소도 없다.
 */
@Schema(description = "강의자료 본문 추출 결과")
data class MaterialExtractResponse(
    @field:Schema(description = "올린 파일 이름. 화면이 \"무엇을 붙였는지\" 보여 주는 데 쓴다")
    val fileName: String,
    @field:Schema(description = "뽑아낸 본문. 상한을 넘으면 앞에서부터 잘려 있다")
    val text: String,
    @field:Schema(description = "본문 글자 수 — 화면의 n / 5000자 표시")
    val charCount: Int,
    @field:Schema(description = "상한을 넘어 뒷부분을 버렸는지. true 면 화면이 알려 준다")
    val truncated: Boolean,
)
