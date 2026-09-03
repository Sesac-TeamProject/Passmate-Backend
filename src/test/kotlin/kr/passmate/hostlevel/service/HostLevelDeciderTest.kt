package kr.passmate.hostlevel.service

import kr.passmate.hostlevel.config.HostLevelProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 등급표 판정 (FR-045~047).
 *
 * 방 100개를 실제로 만들지 않고 등급표 전체를 훑는다 — 판정기가 DB 도 시계도 모르기 때문에
 * 집계 숫자만 바꿔 가며 경계를 확인할 수 있다. 기준은 application.yml 과 같은 값을 쓴다.
 */
class HostLevelDeciderTest {

    private val decider = HostLevelDecider(
        HostLevelProperties(
            ratingSampleMin = 5,
            maintenanceDays = 30,
            levels = listOf(
                HostLevelProperties.Rule(1, "새싹", roomsHosted = 1, totalStudents = 0, unlock = "프로필 뱃지"),
                HostLevelProperties.Rule(2, "성장", roomsHosted = 10, totalStudents = 50),
                HostLevelProperties.Rule(3, "검증된 운영자", roomsHosted = 20, totalStudents = 150, minAvgRating = 4.0, unlock = "유료 방 개설"),
                HostLevelProperties.Rule(4, "인기 운영자", roomsHosted = 40, totalStudents = 400, maintainSessions = 4, maintainAvgRating = 4.0),
                HostLevelProperties.Rule(5, "마스터", roomsHosted = 100, totalStudents = 1000, maintainSessions = 5, maintainAvgRating = 4.0),
            ),
        ),
    )

    @Test
    fun `아무 실적도 없으면 Lv1 이다`() {
        assertThat(decide(1, metrics())).isEqualTo(1)
    }

    @Test
    fun `방 운영과 학생 수를 채우면 Lv2 로 오른다`() {
        assertThat(decide(1, metrics(rooms = 10, students = 50))).isEqualTo(2)
    }

    @Test
    fun `조건 하나만 모자라도 오르지 않는다`() {
        assertThat(decide(1, metrics(rooms = 10, students = 49))).isEqualTo(1)
        assertThat(decide(1, metrics(rooms = 9, students = 50))).isEqualTo(1)
    }

    @Test
    fun `별점 조건이 걸린 Lv3 은 평균이 기준에 닿아야 오른다`() {
        assertThat(decide(2, metrics(rooms = 20, students = 150, avg = 3.9, ratings = 5))).isEqualTo(2)
        assertThat(decide(2, metrics(rooms = 20, students = 150, avg = 4.0, ratings = 5))).isEqualTo(3)
    }

    @Test
    fun `평가가 5건 미만이면 별점이 높아도 승급이 보류된다`() {
        assertThat(decide(2, metrics(rooms = 20, students = 150, avg = 5.0, ratings = 4))).isEqualTo(2)
    }

    @Test
    fun `한 번의 판정으로 여러 단계를 오른다`() {
        assertThat(decide(1, metrics(rooms = 100, students = 1000, avg = 4.5, ratings = 10, active = 5)))
            .isEqualTo(5)
    }

    @Test
    fun `별점 조건에 걸리면 그 위 등급으로도 못 올라간다`() {
        // 방·학생은 Lv.5 조건을 넘지만 Lv.3 의 별점 문턱을 못 넘는다
        assertThat(decide(1, metrics(rooms = 100, students = 1000, avg = 3.0, ratings = 10)))
            .isEqualTo(2)
    }

    @Test
    fun `Lv1에서 Lv3 까지는 활동이 끊겨도 떨어지지 않는다`() {
        assertThat(decide(3, metrics(rooms = 20, students = 150, avg = 4.5, ratings = 5, active = 0)))
            .isEqualTo(3)
    }

    @Test
    fun `Lv4 는 활동이 모자라면 한 단계 내려간다`() {
        assertThat(decide(4, metrics(rooms = 40, students = 400, avg = 4.5, ratings = 5, active = 3)))
            .isEqualTo(3)
    }

    @Test
    fun `Lv4 는 평균 별점이 떨어져도 내려간다`() {
        assertThat(decide(4, metrics(rooms = 40, students = 400, avg = 3.5, ratings = 5, active = 10)))
            .isEqualTo(3)
    }

    @Test
    fun `Lv5 가 미달이면 Lv4 까지만 내려간다 — 두 단계를 한 번에 떨어뜨리지 않는다`() {
        assertThat(decide(5, metrics(rooms = 100, students = 1000, avg = 4.5, ratings = 5, active = 0)))
            .isEqualTo(4)
    }

    @Test
    fun `유지 조건을 채우면 그대로 남는다`() {
        assertThat(decide(4, metrics(rooms = 40, students = 400, avg = 4.5, ratings = 5, active = 4)))
            .isEqualTo(4)
    }

    @Test
    fun `평가 표본이 모자라면 이미 오른 등급을 떨어뜨리지 않는다`() {
        // 표본 미달은 승급을 보류시킬 뿐, 하락 근거는 아니다
        assertThat(decide(4, metrics(rooms = 40, students = 400, avg = 1.0, ratings = 2, active = 4)))
            .isEqualTo(4)
    }

    @Test
    fun `판정한 적 없는 프로필은 승급 직후라 유지 조건을 묻지 않는다`() {
        val justPromoted = decider.decide(
            currentLevel = 1,
            metrics = metrics(rooms = 40, students = 400, avg = 4.5, ratings = 5, active = 0),
            everEvaluated = false,
        )
        assertThat(justPromoted).isEqualTo(4)
    }

    private fun decide(level: Int, metrics: GradeMetrics) =
        decider.decide(level, metrics, everEvaluated = true)

    private fun metrics(
        rooms: Int = 0,
        students: Int = 0,
        avg: Double? = null,
        ratings: Int = 0,
        active: Int = 0,
    ) = GradeMetrics(rooms, students, avg, ratings, active)
}
