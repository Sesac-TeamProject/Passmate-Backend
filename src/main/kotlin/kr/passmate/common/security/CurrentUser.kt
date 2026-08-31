package kr.passmate.common.security

/**
 * 인증된 주체를 컨트롤러 파라미터로 받는다.
 *
 *     fun createRoom(@CurrentUser principal: UserPrincipal, ...)
 *
 * 인증이 없으면 401 로 막는다. 비로그인도 허용하는 화면이면 `required = false` 로 두고
 * 파라미터를 nullable 로 선언한다(게스트 입장 등).
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUser(val required: Boolean = true)
