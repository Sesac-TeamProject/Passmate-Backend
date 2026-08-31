# 배포용 이미지 — GitHub Actions에서 빌드해 ECR로 푸시한다.
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
# EC2 t3.micro(1 GB) — 힙 상한 고정, 스왑 2 GB 필수
ENV JAVA_OPTS="-Xmx512m -XX:+UseSerialGC -Duser.timezone=UTC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
