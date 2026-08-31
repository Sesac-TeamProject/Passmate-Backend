# Passmate-Backend

AI 기반 실시간 문제풀이 플랫폼 **PassMate**의 백엔드 서버.
호스트가 문제 세트를 만들어 방을 열면, 참가자가 PIN·QR로 입장해 실시간으로 문제를 풀고 랭킹·AI 피드백을 받는다.

## 기술 스택

Kotlin 2.2 · JVM 17 · Spring Boot 3.5 · Spring Data JPA · MySQL 8.0 · Flyway · WebSocket(STOMP) · S3 · 포트원(PortOne) · Anthropic API

> Redis는 후순위. 1차 MVP는 MySQL만으로 동작한다 ([.claude/CLAUDE.md](.claude/CLAUDE.md) 참고)

## 로컬 실행

```bash
# 0. 환경변수 준비 (한 번만)
cp .env.example .env

# 1. DB 기동 (MySQL 8)
docker compose -f docker/compose.local.yml up -d

# 2. 앱 실행 — Flyway 마이그레이션 자동 적용, .env 를 환경변수로 주입
./gradlew bootRun --args='--spring.profiles.active=local'

# 3. API 문서
open http://localhost:8080/swagger-ui.html
```

MySQL 은 호스트 **3307** 로 열린다 — 맥에 MySQL 을 직접 깔았을 때 3306 과 부딪히지 않게 하기 위함이다.
스키마가 꼬이면 `docker compose -f docker/compose.local.yml down -v` 후 재기동한다(V1부터 재적용).

```bash
./gradlew test        # 단위 + 통합(Testcontainers 가 MySQL 컨테이너를 자동 관리 — Docker 필요)
```

## 문서

| 문서 | 위치 |
|---|---|
| 작업 규칙 (아키텍처 · 컨벤션 · 금지사항) | [.claude/CLAUDE.md](.claude/CLAUDE.md) |
| 기능 명세서 v2 — US1~20, FR-001~074 | [docs/spec/feature-spec-v2.md](docs/spec/feature-spec-v2.md) |
| 기능 명세서 v1 (원본, 참고용) | [docs/spec/feature-spec-v1-original.md](docs/spec/feature-spec-v1-original.md) |
| ERD v2 — 34 테이블 | [docs/erd/](docs/erd/) (`passmate.dbml` · `passmate-mysql.sql` · `passmate-erd.png`) |
| API 명세서 v2 (117행) · 백엔드 아키텍처 설계 | 노션 — PassMate 문서 |

문서 우선순위: **피그마 v6 화면 > 기능 명세서 v2 > API 명세서 v2 > ERD v2**

## 프로젝트 구조

```
Passmate-Backend/
├── docker/                    compose.local.yml (MySQL 8, Redis 후순위) · Dockerfile (배포 이미지)
├── build.gradle.kts           Spring Boot 3.5 · Kotlin 2.2 · JVM 17
├── docs/                      erd/ · spec/
└── src/main/
    ├── kotlin/kr/passmate/
    │   ├── common/            config · security · exception · domain (필요한 계층만 만든다)
    │   └── {feature}/         controller · service · client · repository · domain · dto
    └── resources/
        ├── application{,-local,-prod}.yml
        └── db/migration/      V1__init.sql (34 테이블)
```

기능 패키지 — `auth` `user` `notification` `room` `question` `session` `scoring` `ai` `feedback` `report` `rating` `hostlevel` `coin` `settlement` `moderation` `ad` `voicehint` `admin`

## 배포

개발은 `develop`, 배포는 `main`. `develop` → `main` PR 머지 → GitHub Actions(test → 이미지 빌드 → ECR) → SSM으로 EC2 배포 → Flyway 적용 → `/actuator/health` 확인.

이미지 빌드는 컨텍스트가 레포 루트다 — `docker build -f docker/Dockerfile -t passmate .`
(Dockerfile 이 루트에 없으므로 워크플로·배포 플랫폼에 경로를 명시해야 한다).
인프라는 AWS 단일 인스턴스(EC2 t3.micro + RDS MySQL + S3).
