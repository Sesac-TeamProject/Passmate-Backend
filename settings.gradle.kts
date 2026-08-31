plugins {
    // JVM 17 툴체인이 없는 머신에서 자동으로 내려받는다 (로컬 JDK는 21이어도 됨)
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "passmate"
