---
name: kotlin-spring-conventions
description: "Donguri(Kotlin 2.3 / Spring Boot 4 / JDK 21) 코드 컨벤션 — 패키지 구조, 컨트롤러/서비스/DTO 역할, 예외와 에러 응답, 검증, 테스트 작성법. Kotlin 파일을 쓰거나 리뷰하기 전에 반드시 읽는다. 새 도메인 패키지를 추가할 때, '이 코드베이스에서는 X를 어떻게 하나' 질문에도 사용한다."
---

# Donguri 컨벤션

포맷은 ktlint(`ktlint_official`, 140자)가 결정한다. 여기에는 ktlint가 못 잡는 구조 규칙만 둔다.

> 현재 코드베이스는 거의 비어 있다. 아래는 **처음 도입할 때 따를 기본값**이다. 실제 코드가 생기면 코드가 우선이며, 이 문서와 어긋나는 패턴이 정착했다면 이 문서를 고친다(같은 PR에서).

## 스택

| 항목 | 값 |
|------|-----|
| Kotlin | 2.3, `-Xjsr305=strict`, `-Xannotation-default-target=param-property` |
| Spring Boot | 4.x — `spring-boot-starter-webmvc`, `-validation`, `-actuator`, `-data-jpa` |
| DB | MySQL 8 (`mysql-connector-j`), 테스트·스모크는 H2 MySQL 모드. 접속 정보는 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` 환경변수 |
| API 문서 | springdoc-openapi 3.x (`/swagger-ui.html`, `/v3/api-docs`), 전역 Bearer JWT 스키마 |
| 보안 | Spring Security + jjwt 0.13 (HS256, `JWT_SECRET`), Stateless, `@EnableMethodSecurity` |
| JSON | Jackson 3 (`tools.jackson.*` 패키지 — `com.fasterxml.jackson` 아님) |
| 테스트 | JUnit Jupiter 6 (Boot 4 BOM, `junit-bom` 6.x), `kotlin-test-junit5`, Boot 4 테스트 스타터(`*-test`) |

Boot 4는 자동설정과 테스트 슬라이스가 모듈별로 쪼개져 import 경로가 3.x와 다르다. **import를 기억으로 쓰지 말고 컴파일로 확인한다.** 새 의존성(DB, 보안 등)은 사용자 확인 후 추가한다.

## 패키지

```
com.donguri
├── <domain>/                 예: acorn/
│   ├── <Domain>Controller.kt
│   ├── <Domain>Service.kt
│   ├── <Domain>.kt           도메인 모델/엔티티
│   ├── <Domain>Repository.kt JpaRepository. Enum 은 타입마다 별도 파일 (예: EmployeeRole.kt, FdsStatus.kt)
│   ├── <Domain>Exception.kt
│   └── dto/                  <Action>Request.kt, <Domain>Response.kt
└── global/
    ├── entity/               BaseTimeEntity (createdAt/updatedAt, Hibernate 자동 기록)
    ├── init/                 DataInitializer (local 프로필 시딩)
    ├── security/             SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter
    ├── util/                 MaskingUtil
    ├── error/                ErrorCode, DonguriException, GlobalExceptionHandler
    └── config/
```

도메인별 패키지(package-by-feature). 레이어별 최상위 패키지(`controller/`, `service/`)를 만들지 않는다.

## 레이어 역할

- **Controller** — HTTP 변환만. `@Valid` 요청 DTO를 받아 서비스를 호출하고 응답 DTO를 돌려준다. 분기/계산/리포지토리 접근 금지. 엔티티를 응답으로 반환하지 않는다.
- **Service** — 비즈니스 규칙과 실패 판단. 실패는 도메인 예외를 던진다(`null` 반환이나 `Boolean` 실패 플래그 금지).
- **DTO** — `data class`. 응답은 `companion object { fun from(x: X) = ... }` 팩토리. 모든 필드를 매핑하고, 원천이 null일 수 있으면 응답도 nullable — 기본값으로 숨기지 않는다.
- **목록 응답**은 배열을 그대로 반환하지 않고 객체로 감싼다: `{ "items": [...] }` (필드 추가가 하위 호환되도록).

## 예외와 에러 응답

던진 예외 타입이 곧 HTTP 상태다. 매핑되지 않은 예외는 500이다.

```kotlin
enum class ErrorCode(val status: HttpStatus, val message: String) {
    ACORN_NOT_FOUND(HttpStatus.NOT_FOUND, "도토리를 찾을 수 없습니다."),
}

open class DonguriException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)

class AcornNotFoundException : DonguriException(ErrorCode.ACORN_NOT_FOUND)
```

- `GlobalExceptionHandler`(`@RestControllerAdvice`)가 `DonguriException` 과 검증 실패(`MethodArgumentNotValidException`)를 `ProblemDetail`(RFC 9457) + `code` 속성으로 변환한다. 첫 엔드포인트를 만들 때 함께 도입한다.
- 서비스에서 `IllegalArgumentException`, `NoSuchElementException`, 맨 `RuntimeException` 을 던지지 않는다(500이 된다). `require`/`check`/`!!` 도 같은 이유로 서비스 경계에서는 쓰지 않는다.
- 사용자에게 보이는 메시지는 한국어, 스택트레이스나 내부 클래스명은 응답에 넣지 않는다.

## 검증

- 요청 DTO에 `jakarta.validation` 제약(`@field:` 불필요 — `param-property` 기본 타깃 설정이 되어 있음)을 달고, **컨트롤러 파라미터에 `@Valid`** 를 붙인다. `@Valid` 가 없으면 제약은 조용히 무시된다.
- 여러 필드에 걸친 규칙이나 상태 의존 규칙은 서비스에서 도메인 예외로.

## 테스트

- 동작 변경 = 테스트 변경. 계획의 실패 케이스 표의 **각 행**에 테스트 하나.
- 컨트롤러: MockMvc(가능하면 `MockMvcTester`)로 **상태 코드와 본문**을 단언한다. "예외가 안 나면 통과"하는 테스트 금지.
- 서비스: 스프링 컨텍스트 없이 순수 단위 테스트. 
- 테스트 이름은 백틱 한국어 문장: `` fun `존재하지 않는 도토리를 조회하면 404를 반환한다`() ``
- `@SpringBootTest` 는 와이어링 확인용 최소한으로. 느리다.
- 테스트를 지우거나 `@Disabled` 로 통과시키지 않는다(CLAUDE.md).

## 설정

- `application.properties` 에 비밀값을 넣지 않는다 — `${ENV_NAME}` 참조 + GitHub Secrets.
- 새 필수 설정은 CD 환경에도 있어야 부팅된다. 추가하면 PR 본문에 명시한다.

## JPA 엔티티

- 생성자 프로퍼티로 필수 값을 받고, 식별자는 본문의 `@Id @GeneratedValue(strategy = IDENTITY) val id: Long? = null`.
- `kotlin("plugin.jpa")` + allOpen(`Entity`, `MappedSuperclass`, `Embeddable`)이 설정돼 있다 — `open` 을 직접 쓰지 않는다.
- 연관관계는 모두 `FetchType.LAZY`, 금액은 `BigDecimal` + `precision/scale` 명시.
- Enum 은 `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`. 없으면 MySQL 네이티브 `enum(...)` 컬럼이 생겨, 상수를 추가해도 `ddl-auto: update` 가 컬럼을 바꾸지 않아 새 값 저장이 실패한다.
  - 그래도 Hibernate 는 허용 값 CHECK 제약(`col in ('A','B')`)을 만든다(`AttributeConverter` 를 써도 동일, Hibernate 7). `ddl-auto: update` 는 이 제약을 고치지 않는다. **Enum 상수를 추가하면 같은 PR 에서 기존 DB 의 CHECK 제약을 바꾸는 SQL 을 PR 본문에 적는다** — 마이그레이션 도구(Flyway) 도입 전까지의 규칙.
- 엔티티를 컨트롤러 응답으로 반환하지 않는다(위 레이어 역할). `open-in-view` 는 꺼져 있으므로 지연 로딩은 서비스의 트랜잭션 안에서 끝낸다.
- 테스트: 매핑은 `@DataJpaTest`(`org.springframework.boot.data.jpa.test.autoconfigure`) + `@AutoConfigureTestDatabase(replace = NONE)` + `@ActiveProfiles("test")` — 임의 내장 DB 가 아니라 test 프로필의 H2 MySQL 모드로 검증한다.
- `local` 프로필(초기 데이터 시딩)은 `bootRun` 에서만 기본으로 켜진다. 이미지(bootJar)는 프로필 기본값이 없다.

## 보안

- 허용 경로는 `SecurityConfig` 에 명시한 것(`POST /api/auth/login`, health, swagger, `/error`)뿐이고 나머지는 인증이 필요하다. 새 공개 경로는 그 목록에 추가하고 테스트한다.
- 역할 제한은 컨트롤러 메서드의 `@PreAuthorize("hasRole('FDS_ADMIN')")`. 권한 이름은 `ROLE_` + `EmployeeRole` 이름.
- 현재 사용자는 `SecurityContextHolder` 의 principal `JwtPrincipal`(username, role).
- 401/403 도 `ErrorCode.UNAUTHORIZED`/`ACCESS_DENIED` ProblemDetail 로 응답한다(`HandlerExceptionResolver` 위임).
- 고객 이름·계좌번호를 응답에 담을 때는 `MaskingUtil.name`/`accountNumber` 를 거친다(FDS_ADMIN 만 원본).
- 보안 테스트는 `@SpringBootTest` + `@AutoConfigureMockMvc`(`org.springframework.boot.webmvc.test.autoconfigure`)로 상태 코드와 `code` 를 단언한다.
