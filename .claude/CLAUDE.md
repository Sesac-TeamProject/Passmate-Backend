# PassMate Backend — 작업 규칙

AI 기반 실시간 문제풀이 플랫폼. 호스트가 방을 만들고 참가자가 PIN으로 입장해 실시간으로 문제를 푼다.
Spring Boot 3.5 · Kotlin 2.2 · JVM 17 · MySQL 8.0 · S3 · 포트원(PortOne).

## ⚠️ 코드만으로 끝나지 않는 기능 — 사용자 작업을 먼저 알린다

외부 콘솔 설정·계정 발급·키 등록처럼 **사용자가 직접 해야 완성되는** 기능이 많다.
코드를 다 짜도 그 작업이 끝나기 전까지 기능은 동작하지 않는다.

1. **구현 전에 알린다** — 어디서(콘솔·서비스), 무엇을, 어떤 값을 받아 어디에 넣는지 목록으로 준다
2. **"완료"라고 말하지 않는다** — `코드 완료 / 기능 미완`으로 구분해서 보고한다. 사용자 작업이 남아 있으면 그 기능은 미완이다
3. **사용자 작업이 끝난 것을 확인한 뒤에 다음 작업을 제안한다** — 확인은 말이 아니라 실제 호출·조회로 검증한다
4. 사용자 작업이 남아 있으면 **다음 작업으로 넘어가지 않고 멈춘다**. 막히지 않은 다른 일을 먼저 하는 게 나아 보이면, 그걸 제안하되 **결정은 사용자가 한다**

### 사용자 작업 현황

| 기능 | 사용자가 할 일 | 받는 값 → 넣을 곳 | 상태 |
|---|---|---|---|
| Google 로그인 | Google Cloud → OAuth 동의 화면 + 클라이언트 ID(웹·Android·iOS) | 웹 클라이언트 ID → `GOOGLE_CLIENT_ID` | 🟨 웹 클라이언트 ID 설정 완료 · **실제 로그인 확인은 연동 시점**(그때까지 `dev-login` 유지) |
| AI 문제 생성 | Anthropic 콘솔에서 API 키 발급 | `ANTHROPIC_API_KEY` | ⬜ 대기 |
| 코인 충전·결제 | 포트원 가입 → 테스트 채널 · 웹훅 등록 | `PORTONE_STORE_ID` · `PORTONE_API_SECRET` · `PORTONE_WEBHOOK_SECRET` | ⬜ 대기 |
| 파일 업로드 | S3 버킷 생성 + IAM 사용자·정책 | `S3_BUCKET` · `AWS_REGION` · 자격증명 | ⬜ 대기 |
| 푸시 알림 | Firebase 프로젝트 + 서비스 계정 키 | FCM 자격증명 | ⬜ 대기 |
| 배포 | AWS 계정 · 도메인 구입 · 인증서(certbot) | EC2 · RDS · SSM 파라미터 | ⬜ 대기 |

> 앱(Android·iOS)은 자기 클라이언트 ID가 아니라 **웹 클라이언트 ID를 `serverClientId`로** 지정해 ID 토큰을 받아야 `aud` 검증을 통과한다.
> 운영 리디렉션 URI 등록에는 **https 도메인**이 필요하므로 도메인·인증서가 Google 설정의 선행 조건이다.

## 문서 (진실의 원천)

충돌하면 **피그마 v6 화면 > 기능 명세서 v2 > API 명세서 v2 > ERD v2** 순으로 위쪽이 이긴다.
스키마나 API를 바꿀 때는 **문서를 먼저 고치고** 코드를 바꾼다.

| 문서 | 위치 |
|---|---|
| 기능 명세서 v2 (US1~20, FR-001~074) | `docs/spec/feature-spec-v2.md` |
| ERD v2 (34 테이블) | `docs/erd/passmate.dbml`, `docs/erd/passmate-mysql.sql` |
| API 명세서 v2 (117행), 백엔드 아키텍처 설계 | 노션 — 개인 워크스페이스 "PassMate 문서 (개인 사본)" |

## 아키텍처

레이어드(Controller → Service → Repository / Client) + **패키지 바이 피처**. 루트 패키지 `kr.passmate`.

```
kr/passmate/
├── common/      config · security · exception · redis · storage · event · domain · util
└── {feature}/   controller · service · client · repository · domain · dto   (없는 계층은 만들지 않음)
```

기능 패키지: `auth user notification room question session scoring ai feedback report rating hostlevel coin settlement moderation ad voicehint admin`

- 기능 간 참조는 **Service → 다른 기능의 Service**만. 다른 기능의 Repository·엔티티 직접 접근 금지
- 순환 참조가 생기면 `common/event`의 ApplicationEvent로 끊는다
- `admin/`은 자체 도메인 로직을 갖지 않는다 — 각 기능 Service를 호출하고 `AdminActivityLog`만 남긴다
- 배치 `*Job`은 소유 기능 패키지 안에 두고 **멱등**하게 작성한다

### 계층 규칙

- **Controller** — `@Valid` 검증, `@CurrentUser` 주체 해석, Service 호출, DTO 응답. 비즈니스 로직·엔티티 반환·`@Transactional`·try/catch 금지
- **Service** — 유스케이스 1개 = 메서드 1개, `@Transactional` 경계. 조회 전용은 `XxxQueryService`(`readOnly = true`). **외부 API 호출(포트원·Anthropic)은 트랜잭션 밖**에서 하고 결과만 반영
- **Client** — 외부 시스템(Anthropic · PortOne · Google · FCM · S3)은 인터페이스 + 구현. 실패는 `BusinessException`으로 번역. Service가 SDK·HTTP를 직접 다루지 않는다
- **Repository** — 같은 기능 패키지의 Service에서만 주입. 복잡한 조회는 `XxxQueryRepository`(QueryDSL)
- **Domain** — 엔티티는 `BaseTimeEntity` 상속, setter 없음. 상태 전이는 엔티티 메서드(`room.start()`, `charge.markPaid()`, `wallet.deduct()`)로 하고 검증도 그 안에서. 연관관계는 `@ManyToOne(fetch = LAZY)` 단방향 기본
- **DTO** — `XxxRequest` / `XxxResponse` data class, `companion object { fun from(entity) }`. 검증 애노테이션은 Request에만

의존 방향은 `controller → service → repository / client → domain`. `domain`은 어느 계층도 import하지 않는다.

## API 규칙

- **URL prefix 없음** — `/api/v1` 붙이지 않는다. 경로는 API 명세서 그대로
- 수정은 `PUT`. 상태 코드 명시: 생성 201, 삭제 204, 코인 부족 402, 권한 없음 403
- 오류는 `ErrorCode`(enum, status+code+message) → `BusinessException` → `GlobalExceptionHandler` → `{code, message}`. 내부 원인은 로그에만
- **토큰 만료는 401**(`JwtAuthenticationEntryPoint`). 403으로 응답하면 클라이언트 refresh가 발화하지 않는다
- 권한 게이트는 전부 서버에서: 호스트 검증 · Lv.3+ 유료 방(403 `HOST_LEVEL_REQUIRED`) · 코인 부족(402 `INSUFFICIENT_COINS`) · 제재 계정 거부 · ADMIN(`/admin/*`)
- 정책값(참가비 범위 · 최소 정산액 · AI 무료 한도 · 평가 가능 24h)은 `PolicyProperties`로 env 바인딩. 하드코딩 금지

## DB

- **스키마 변경은 Flyway로만**: `src/main/resources/db/migration/V{n}__{설명}.sql`. 이미 적용된 파일은 절대 수정하지 않고 새 파일을 추가한다
- `spring.jpa.hibernate.ddl-auto: validate` 고정. `update`·`create` 사용 금지
- DDL 원본은 `docs/erd/passmate-mysql.sql`(34 테이블). 엔티티 클래스명은 테이블명을 그대로 따른다(`teacher_review` → `TeacherReview`, `report` → `Report`(신고))
- 무결성은 DB 제약에 둔다: 닉네임 UK(room, nickname) · 평가 UK(room, participant) · `coin_wallet.balance >= 0` · `coin_transaction` append-only
- 코인 동시성은 `SELECT … FOR UPDATE` 비관적 락

## ⚠️ Redis는 후순위 — 지금은 쓰지 않는다

MVP 1차에서는 **Redis를 도입하지 않는다.** `docker/compose.local.yml`에도 MySQL만 올린다.
아키텍처 문서의 Redis 설계는 2차 도입 시점의 목표이고, 지금은 아래 대체안으로 구현한다.

| 원래 Redis 용도 | 지금 구현 | 나중 전환 |
|---|---|---|
| 세션 진행 상태 · 랭킹 | MySQL — `answer` 저장 후 `SUM(score) GROUP BY participant` 집계 | `RoomStateRepository` 구현체 교체 |
| PIN 유일성 | `PinService`가 6자리 생성 → 활성 방(`WAITING`/`RUNNING`) 중복 조회 → 충돌 시 재생성 | `SETNX` |
| refresh 토큰 | stateless JWT 검증(서명 + 만료). 로그아웃은 클라이언트가 토큰 폐기 | Redis TTL + 즉시 무효화 |
| AI 무료 한도 | `ai_generation_log`에서 `kind='SET' AND status='SUCCESS'` COUNT | Redis 카운터 |
| 코인 락 | DB 비관적 락 (원래도 이쪽이 주(主)) | 그대로 |

**지켜야 할 것**: 세션 상태 접근은 반드시 `RoomStateRepository` **인터페이스**를 통한다. 구현체(`JpaRoomStateRepository`)를 Service가 직접 알지 않게 해야 나중에 Redis 도입이 구현체 추가로 끝난다.

WebSocket/STOMP는 Redis와 무관하다(simple broker = 인메모리). 실시간 기능은 Redis 없이 전부 동작한다.

## 실시간

- **제어는 REST, 전파는 WebSocket**. 호스트의 start · next · close · lock · finish는 REST 호출 → 서버 상태 변경 → STOMP 브로드캐스트. 클라이언트가 WS로 상태를 바꾸는 경로는 만들지 않는다
- 토픽: `/ws` · 방 `/topic/rooms/{roomId}` · 호스트 `/topic/rooms/{roomId}/host` · 개인 `/user/queue/*`. 구독 인가 인터셉터로 참가자/호스트 검증
- **서버 권위 타이머**: 문항 시작 시 서버가 `endsAt`을 발급·브로드캐스트하고 만료 시 `QUESTION_ENDED`로 마감. 클라이언트 시계를 신뢰하지 않는다
- **정답과 응답 분포는 `QUESTION_ENDED` 페이로드에만.** `QUESTION_STARTED`에 정답을 절대 포함하지 않는다
- 재접속은 `GET /rooms/{roomId}/session` 스냅샷으로 복구

## AI

- 문제 생성은 **동기**(30초 SLA), Structured Outputs 스키마 강제 → 형식 오류 1회 재시도 → 실패 502 `AI_GENERATION_FAILED`(무료 횟수 미차감)
- 서술형 분석은 **`@Async` + 세마포어**. 세션 실시간 경로를 절대 막지 않는다. 상태 PENDING / DONE / FAILED / SKIPPED
- 모델은 `LLM_MODEL` env(기본 `claude-opus-5`, 개발·부하 테스트는 `claude-haiku-4-5`)
- 사용자 입력(주제·강의자료)은 지시문과 분리된 컨텍스트 블록으로 주입한다(프롬프트 인젝션 완화)

## 테스트

- 단위 = MockK, 통합 = Testcontainers(MySQL). 외부 Client는 Fake 구현으로 대체
- 필수 케이스: 점수 공식 경계(만료 직전 · 오답 0점 · 서술형 보정) · 코인 원장 = 지갑 잔액 · 웹훅 멱등 · 환급/등급/게스트 파기 · 401·refresh·게스트 토큰 스코프
- 커밋 전 `./gradlew test` 통과 확인

## 로컬 실행

```bash
docker compose -f docker/compose.local.yml up -d    # MySQL 8
./gradlew bootRun --args='--spring.profiles.active=local'     # Flyway 적용 + 시드
```

프로파일은 `local` / `prod` 두 개. 시드(`DevSeedRunner`)와 `POST /auth/dev-login`은 `@Profile("local","dev")` 한정.
스키마가 꼬이면 `docker compose -f docker/compose.local.yml down -v` 후 재기동(V1부터 재적용).

### 시크릿 취급

`.env` 처럼 시크릿이 들어 있을 수 있는 파일은 **값을 출력하지 않는다.** 필요한 정보는 대개 "설정됐는가" 하나뿐이다.

```bash
grep -q '^GOOGLE_CLIENT_ID=..' .env && echo "설정됨" || echo "없음"   # 존재 여부만
```

부득이 여러 줄을 봐야 하면 **기본을 전량 마스킹**으로 두고 시작한다 — 특정 값 형태만 가리는 방식은
형태가 다른 줄(예: `GOCSPX-…` 시크릿)이 그대로 새어 나간다.

```bash
sed -E 's/=.*/=***/' .env        # 키 이름만 확인
```

한 번 출력된 값은 대화·로그·세션 기록에 남아 되돌릴 수 없다. 노출됐다면 **해당 시크릿을 재발급**하고 알린다.

## Git

- 상시 브랜치는 **`main` · `develop`** 둘. `main`은 배포 가능한 상태만 담고, 개발은 전부 `develop`에서 한다 — **push도 `develop`으로** 한다
- `main`에 직접 커밋·push 금지. `develop` → `main`은 PR로만 병합하고, 그 병합이 배포 트리거다
- **모든 작업은 `develop`에서 분기한다** — `feat/…` · `fix/…` 브랜치를 파서 작업하고 PR로 `develop`에 병합한다. 예외 없음
- 커밋 메시지는 한국어, 형식 `feat: 방 생성 API 구현`
- **커밋까지만 알아서 하고 멈춘다. `push` · PR 생성 · 머지는 매번 사용자에게 물어보고 진행한다** — 원격을 바꾸는 일은 사용자 결정이다
- 기능 단위 작업이 끝나면 `develop`에 PR을 올려(확인 후) 머지하고, 다음 작업은 새 브랜치를 파서 시작한다. 머지된 브랜치에서 이어서 작업하지 않는다
- PR·이슈는 `.github/` 템플릿을 따른다. CONTRIBUTING.md · CODEOWNERS 도 루트가 아니라 `.github/` 안에 둔다(깃허브가 거기서도 인식한다)
- **PR 본문은 짧게** — `한 일` · `남은 일/확인 필요` · `이슈·공유사항`의 핵심만. 코드 설명을 늘어놓지 않는다. 템플릿에서 비는 항목은 지운다
- **루트는 비워 둔다** — 규칙은 `.claude/`, 도커 설정은 `docker/`, 깃허브 설정은 `.github/`, 문서는 `docs/`. 빌드 파일(`build.gradle.kts` · `gradlew`)과 `README.md` 처럼 루트에 있어야 동작하는 것만 남긴다
- 레포에는 소스만 둔다. 빌드 산출물(`build/` `.gradle/` `.kotlin/`)·시크릿(`.env`)·개인 IDE 설정은 커밋하지 않는다 — 배포 이미지는 `docker/Dockerfile` 이 소스에서 다시 빌드한다

## 하지 말 것

1. `/api/v1` 같은 URL prefix 붙이기
2. `ddl-auto: update`로 스키마 만들기, 적용된 Flyway 파일 수정하기
3. 컨트롤러에서 엔티티 반환, try/catch, 트랜잭션 열기
4. 다른 기능 패키지의 Repository·엔티티 직접 참조
5. 토큰 만료에 403 응답
6. `QUESTION_STARTED`에 정답 포함
7. 클라이언트가 보낸 결제 금액 신뢰(포트원 조회 API로 대조 후 확정)
8. **Redis 관련 코드·의존성 추가** (후순위 결정, 위 §Redis 참고)
9. 이메일/비밀번호 인증 구현 (Google 로그인 단일, 해당 API 5건은 보류)
10. `main`에 직접 커밋·push (작업과 push는 `develop`에서)
11. 외부 설정이 남은 기능을 "완료"라고 보고하거나, 사용자 작업 확인 전에 다음 작업을 제안하기
12. `.env` 등 시크릿이 있을 수 있는 파일의 값을 출력하기 (존재 여부만 확인, 부득이하면 전량 마스킹)
13. 사용자 확인 없이 `push` · PR 생성 · 머지 진행하기 (커밋까지만 하고 물어본다)
