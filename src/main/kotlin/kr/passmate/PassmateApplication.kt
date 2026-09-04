package kr.passmate

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.TimeZone

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
class PassmateApplication {

    /**
     * 시간 계약은 "오프셋 없는 UTC"(API 명세). 운영 컨테이너는 -Duser.timezone=UTC 로 뜨지만
     * 로컬 bootRun 은 머신 시간대(KST)로 떠서 endsAt 이 9시간 어긋났다(프론트 QA_BACKLOG B-1) —
     * 어디서 띄우든 같은 동작이 되도록 기동 시점에 강제한다.
     * main 이 아니라 여기에도 두는 이유 — @SpringBootTest 는 main 을 타지 않는다.
     */
    @PostConstruct
    fun forceUtc() {
        TimeZone.setDefault(UTC)
    }

    companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    }
}

fun main(args: Array<String>) {
    TimeZone.setDefault(PassmateApplication.UTC)
    runApplication<PassmateApplication>(*args)
}
