# Render + TiDB Cloud 배포 (무료)

백엔드(Spring Boot)는 Render 무료 웹 서비스, DB 는 TiDB Cloud Starter(MySQL 호환)에 올린다. 프론트엔드는 Vercel 에 따로 올린다.
두 서비스 모두 **카드 등록 없이** 무료 플랜으로 쓴다.

```
main 머지 → GitHub Actions(CI check, CD image) 모두 성공 → Render 가 같은 커밋을 Dockerfile 로 빌드 → 헬스체크 통과 시 교체
```

- 빌드·배포 설정은 저장소 루트의 [`render.yaml`](../../render.yaml) 이 정한다 (무료 플랜, 싱가포르 리전, Dockerfile 빌드, `/actuator/health` 헬스체크, CI 통과 후 배포).
- Render 토큰은 GitHub 에 두지 않는다. Render 가 GitHub 연동으로 `main` 을 직접 가져간다.
- 운영 비밀값은 Render 서비스 환경변수에만 둔다.

## 무료 플랜에서 알아 둘 것

| 항목 | 내용 |
|------|------|
| Render 인스턴스 | 0.1 CPU / 512 MB. 월 750 시간 (서비스 하나를 계속 켜 둬도 남는다) |
| 잠들기 | 15분 동안 요청이 없으면 잠든다. 다음 요청이 깨우며, 앱이 다시 뜰 때까지 약 3~4분 걸린다 (CD 스모크의 0.1 CPU·512 MB·스왑 없음 실측 약 4분). 프론트는 첫 API 호출의 타임아웃을 넉넉히 두거나 "서버를 깨우는 중" 안내를 보여 준다 |
| 재시작 | Render 가 언제든 재시작할 수 있다. 디스크는 휘발성이다 (상태는 DB 에만 둔다) |
| 롤백 | 직전 배포 2개까지만 |
| TiDB 무료 한도 | 인스턴스당 저장 5 GiB, 월 50M Request Unit. 넘으면 그 달 남은 기간 동안 느려지거나 접속이 거부된다 — 카드가 없으면 과금되지 않는다 |
| TiDB 커넥션 | TLS 필수. 30분 넘은 커넥션은 끊길 수 있어 `render.yaml` 에서 Hikari 최대 수명을 5분으로 둔다 |

## 1. TiDB Cloud Starter

1. https://tidbcloud.com 에 GitHub 또는 Google 계정으로 가입한다.
2. **Create Instance** → **Starter**(무료). 리전은 AWS 아시아 리전 중 싱가포르(`ap-southeast-1`)가 있으면 그것을 고른다 (Render 리전과 가깝다).
3. 인스턴스의 **SQL Editor** 에서 DB 를 만든다:
   ```sql
   CREATE DATABASE safeflow;
   ```
   스키마(테이블)는 앱 기동 시 Flyway 가 만든다.
4. **Connect** → 연결 방식 **Public**, 클라이언트 **General** 을 고르고 비밀번호를 생성한다. 화면의 `HOST`, `USERNAME`(앞에 `xxxx.` 접두사가 붙는다), `PASSWORD` 를 적어 둔다.
5. 방화벽(허용 IP) 설정이 있으면 Render 서비스의 **Outbound IP**(Render 서비스 → **Connect** → **Outbound**)를 추가한다.

## 2. Render 서비스

> **`render.yaml` 이 `main` 에 들어간 뒤(develop → main 릴리스 머지 후)에 만든다.**
> 저장소 기본 브랜치는 `develop` 이다. Blueprint 를 `develop` 으로 만들면 develop 에 머지한 `render.yaml` 변경이 릴리스 없이 운영 서비스 설정에 반영된다.

1. https://render.com 에 GitHub 계정으로 가입한다.
2. **New** → **Blueprint** → 저장소 `HAAHYO/SafeFlow-Server` 를 고르고, **Branch** 를 `main` 으로 바꾼다. `render.yaml` 을 읽어 `safeflow-server` 웹 서비스(Free)를 보여 준다.
   - 브랜치가 `main` 인지 확인한다. `develop` 으로 만들었다면 Blueprint 를 지우고 `main` 으로 다시 만든다 (서비스의 배포 브랜치는 `render.yaml` 의 `branch: main` 이 정한다).
   - 플랜이 **Free** 로 표시되는지 확인한다. 다른 플랜이 보이면 만들지 않는다.
3. `sync: false` 로 둔 변수 값을 입력한다:

```
DB_URL=jdbc:mysql://<HOST>:4000/safeflow?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Seoul&characterEncoding=UTF-8&sessionVariables=innodb_lock_wait_timeout=3
DB_USERNAME=<접두사가 붙은 USERNAME>
DB_PASSWORD=<PASSWORD>
JWT_SECRET=<openssl rand -base64 32 결과>
CORS_ALLOWED_ORIGINS=https://<프론트>.vercel.app
```

- `sslMode=VERIFY_IDENTITY` 는 빼지 않는다. TiDB Cloud 는 TLS 가 아닌 접속을 거부한다. 인증서는 Let's Encrypt 라 JVM 기본 truststore 로 검증된다.
- `sessionVariables=innodb_lock_wait_timeout=3` 도 빼지 않는다. 없으면 락 대기가 50초가 된다.
- `JWT_SECRET` 이 없으면 prod 프로필은 기동하지 않는다.
- `CORS_ALLOWED_ORIGINS` 는 브라우저에서 API 를 부를 프론트 주소다. 쉼표로 여러 개를 넣을 수 있고 `https://*.example.com` 같은 패턴도 된다. 비우면 앱은 뜨지만 브라우저 호출은 모두 CORS 로 막힌다. Vercel 미리보기 주소(`*.vercel.app`) 전체를 넣으면 다른 사람의 Vercel 앱도 호출할 수 있으므로, 프로덕션 도메인만 넣는다.
- `PORT` 는 설정하지 않는다. Render 가 주고, 앱은 `server.port: ${PORT:8080}` 으로 그 포트에 뜬다.
- `SPRING_PROFILES_ACTIVE`, Hikari 설정은 `render.yaml` 이 넣는다. 대시보드에서 바꾸지 않는다 (바꾸면 파일과 어긋난다).

4. **Apply** 로 만든다. 이후에는 `main` 에 머지되고 GitHub Actions 가 모두 통과한 커밋마다 자동 배포된다.

## 3. 주소

서비스 페이지 위쪽의 `https://safeflow-server-xxxx.onrender.com` 이 API 주소다. 이 주소를 Vercel 프론트의 API 주소 환경변수로 쓴다.

## 4. 첫 배포와 확인

1. 서비스 **Events** 에서 배포가 **Deploy live** 인지 확인한다.
2. `curl https://<주소>/actuator/health` → `{"status":"UP"}` (잠들어 있었다면 첫 응답까지 약 3~4분 걸린다)

## 문제가 생겼을 때

| 증상 | 원인과 조치 |
|------|-------------|
| `main` 에 머지했는데 배포가 시작되지 않음 | 그 커밋의 GitHub Actions 중 하나가 실패했거나 아직 실행 중 (`checksPass`). Actions 결과를 본다. 수동 배포는 **Manual Deploy** |
| 헬스체크 실패 | 새 배포가 기동하지 못함. **Logs** 에서 원인(대개 DB 접속, Flyway, 변수)을 보고 고친 뒤 다시 PR. 마이그레이션이 적용됐거나 중간에 실패했다면 아래 "롤백과 DB 마이그레이션" 을 확인 |
| `Communications link failure` | `DB_URL` 의 HOST·포트(4000) 오류, 또는 TiDB 방화벽이 Render Outbound IP 를 막음 |
| `Access denied for user` | `DB_USERNAME` 에 접두사(`xxxx.`)가 빠짐, 또는 비밀번호 오류 |
| `Connections using insecure transport are prohibited` 류 | `DB_URL` 에 `sslMode=VERIFY_IDENTITY` 누락 |
| `OutOfMemoryError` / 재시작 반복 | 512 MB 초과. CD 스모크가 같은 제한으로 기동을 확인하므로, 스모크는 통과했는데 운영에서만 나면 요청 처리 중 메모리 사용을 본다 |
| `JWT_SECRET` 관련 기동 실패 | 2단계 변수 누락 |

### 롤백과 DB 마이그레이션

새 배포가 기동 중에 실행한 Flyway 마이그레이션은 배포가 실패해도 DB 에 남는다.

- **마이그레이션은 직전 버전과 호환되게 쓴다.** 컬럼·테이블 추가는 안전하다 (구 버전 앱은 새 마이그레이션을 미래 버전으로 무시하고, `ddl-auto: validate` 는 엔티티에 있는 컬럼만 확인한다). 단, `NOT NULL` 컬럼은 `DEFAULT` 를 함께 준다 — 없으면 구 버전의 그 테이블 INSERT 가 모두 `doesn't have a default value` 로 실패한다. 컬럼 삭제·이름 변경·타입 축소는 구 버전의 `validate` 를 깨므로, 코드에서 먼저 사용을 끊고 다음 릴리스에서 지운다.
- **TiDB 에서 쓰지 않는 것:** `CHECK` 제약은 TiDB 기본 설정에서 조용히 무시된다 (값 검증은 애플리케이션 enum 이 맡는다). `TEXT`/`BLOB` 컬럼에는 외래 키를 걸 수 없다. `AUTO_INCREMENT` 값은 유일하지만 연속적이지 않다 — id 순서나 간격에 의존하지 않는다.
- **마이그레이션이 중간에 실패한 경우:** TiDB 의 DDL 도 트랜잭션으로 되돌려지지 않는다. 앞부분 DDL 은 남고 `flyway_schema_history` 에 `success = 0` 행이 생긴다.
  - 고친 버전을 다시 배포해도 같은 버전의 실패 행 때문에 검증에서 막힌다.
  - 확인: Render **Logs** 에서 `Migration of schema` / `failed` 를 찾거나, TiDB **SQL Editor** 에서 `SELECT version, description FROM safeflow.flyway_schema_history WHERE success = 0;`
  1. 실패한 스크립트에서 이미 적용된 DDL 을 DB 에서 직접 되돌린다.
  2. 실패 행을 지운다 (`flyway repair` 와 같은 효과): `DELETE FROM safeflow.flyway_schema_history WHERE success = 0;`
  3. 고친 마이그레이션으로 다시 PR → 머지. 이미 성공 기록된 버전 파일은 고치지 않는다 (체크섬 불일치) — 새 버전 번호로 추가한다.

### 수동 롤백

서비스 **Events** → 되돌릴 이전 배포의 **Rollback**. 무료 플랜은 직전 배포 2개까지만 되돌릴 수 있다. 위 마이그레이션 호환 규칙이 그대로 적용된다.
