# 배포용 이미지 — GitHub Actions에서 빌드해 ECR로 푸시한다.
# 빌드 컨텍스트는 레포 루트다:  docker build -f docker/Dockerfile -t passmate .
# 베이스는 amazoncorretto:17-alpine — eclipse-temurin 의 alpine 태그는 amd64 전용이라
# arm64 맥에서 빌드가 깨진다. corretto alpine 은 amd64·arm64 둘 다 있고 AWS 배포와도 맞는다.
FROM amazoncorretto:17-alpine AS builder
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM amazoncorretto:17-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /workspace/build/libs/app.jar app.jar
USER app
EXPOSE 8080
# 로컬 검증용. 운영 힙 설정은 docker/Dockerfile.runtime 을 따른다
ENV JAVA_OPTS="-Xmx640m -XX:+UseSerialGC -Duser.timezone=UTC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
