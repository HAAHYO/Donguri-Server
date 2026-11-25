# Railway 배포

백엔드(Spring Boot)와 MySQL 을 Railway 에 올린다. 프론트엔드는 Vercel 에 따로 올린다.

```
main 머지 → GitHub Actions(CI check, CD image) 성공 → Railway 가 같은 커밋을 Dockerfile 로 빌드 → 헬스체크 통과 시 교체
```

- 빌드·배포 설정은 저장소 루트의 [`railway.json`](../../railway.json) 이 정한다 (Dockerfile 빌드, `/actuator/health` 헬스체크, 실패 시 재시작).
- Railway 토큰은 GitHub 에 두지 않는다. Railway 가 GitHub 연동으로 `main` 을 직접 가져간다.
- 운영 비밀값은 Railway 서비스 변수에만 둔다.

## 1. 프로젝트와 MySQL

1. https://railway.com 에 GitHub 계정으로 로그인 → **New Project** → **Empty Project**.
2. 프로젝트 캔버스에서 **+ Create** → **Database** → **MySQL**. 서비스 이름은 기본값 `MySQL` 을 그대로 둔다 (아래 변수 참조가 이 이름을 쓴다).
3. 스키마는 앱 기동 시 Flyway 가 만든다. MySQL 서비스가 만드는 기본 DB(`railway`)를 그대로 쓴다.

## 2. 백엔드 서비스

1. **+ Create** → **GitHub Repo** → `HAAHYO/Donguri-Server`.
2. 서비스 **Settings**:
   - **Source → Branch:** `main`
   - **Wait for CI:** 켠다. GitHub Actions 가 성공한 커밋만 배포한다 — 테스트나 이미지 스모크가 실패한 커밋은 배포되지 않는다.
   - 빌드 방식·헬스체크는 `railway.json` 이 정하므로 대시보드에서 바꾸지 않는다 (바꾸면 파일과 어긋난다).
3. 서비스 **Variables** (Raw Editor 에 붙여 넣기):

```
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&sessionVariables=innodb_lock_wait_timeout=3
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
JWT_SECRET=<openssl rand -base64 32 결과>
```

- `${{MySQL.…}}` 는 Railway 변수 참조다. 값을 직접 복사하지 않아도 같은 프로젝트의 MySQL 접속 정보가 들어가고, 내부 네트워크로 접속한다.
- `DB_URL` 의 `sessionVariables=innodb_lock_wait_timeout=3` 는 빼지 않는다. 없으면 락 대기가 50초가 된다.
- `JWT_SECRET` 이 없으면 prod 프로필은 기동하지 않는다.
- `PORT` 는 설정하지 않는다. Railway 가 주고, 앱은 `server.port: ${PORT:8080}` 으로 그 포트에 뜬다.

## 3. 도메인

서비스 **Settings → Networking → Generate Domain** → `https://<이름>.up.railway.app`. 이 주소를 Vercel 프론트의 API 주소 환경변수로 쓴다.

## 4. 첫 배포와 확인

1. 저장소를 연결하는 순간 Railway 가 변수 없이 첫 배포를 시작한다 — 이 배포는 DB 접속·`JWT_SECRET` 이 없어 실패하는 게 정상이다. 변수를 저장하면 다시 배포한다. 이후에는 `main` 머지마다 자동 배포된다.
2. 서비스 **Deployments** 에서 상태가 **Active** 인지 확인한다.
3. `curl https://<도메인>/actuator/health` → `{"status":"UP"}`

## 문제가 생겼을 때

| 증상 | 원인과 조치 |
|------|-------------|
| 배포가 `Waiting for CI` 에서 멈춤 | 그 커밋의 GitHub Actions 가 실패했거나 아직 실행 중. Actions 결과를 본다 |
| `Healthcheck failed` | 새 배포가 기동하지 못함 — **직전 배포가 계속 서비스한다** (성공한 배포가 아직 없으면 서비스 중인 것도 없다). Deploy Logs 에서 원인(대개 DB 접속, Flyway, 변수)을 보고 고친 뒤 다시 PR. 마이그레이션이 적용됐거나 중간에 실패했다면 아래 "롤백과 DB 마이그레이션" 을 확인 |
| `Communications link failure` | `DB_URL` 의 변수 참조 이름이 MySQL 서비스 이름과 다름 (`${{MySQL.…}}`) |
| `JWT_SECRET` 관련 기동 실패 | 2단계 변수 누락 |

### 롤백과 DB 마이그레이션

헬스체크 실패 시 Railway 는 새 배포를 활성화하지 않고 직전 배포가 계속 서비스한다. 하지만 새 배포가 기동 중에 실행한 Flyway 마이그레이션은 DB 에 남는다.

- **마이그레이션은 직전 버전과 호환되게 쓴다.** 컬럼·테이블 추가는 안전하다 (구 버전 앱은 새 마이그레이션을 미래 버전으로 무시하고, `ddl-auto: validate` 는 엔티티에 있는 컬럼만 확인한다). 단, `NOT NULL` 컬럼은 `DEFAULT` 를 함께 준다 — 없으면 구 버전의 그 테이블 INSERT 가 모두 `doesn't have a default value` 로 실패한다. 컬럼 삭제·이름 변경·타입 축소는 구 버전의 `validate` 를 깨므로, 코드에서 먼저 사용을 끊고 다음 릴리스에서 지운다.
- **마이그레이션이 중간에 실패한 경우:** MySQL 의 DDL 은 트랜잭션으로 되돌려지지 않는다. 앞부분 DDL 은 남고 `flyway_schema_history` 에 `success = 0` 행이 생긴다.
  - 직전 배포는 계속 서비스하지만 **스키마는 반쯤 바뀐 상태**다. 직전 배포가 재시작돼도 실패 행을 "미래의 실패"로 보고 경고만 남긴 채 뜨므로 겉으로는 정상처럼 보인다.
  - 고친 버전을 다시 배포해도 같은 버전의 실패 행 때문에 검증에서 막힌다.
  - 확인: Deploy Logs 에서 `Migration of schema` / `failed` 를 찾거나, MySQL 서비스 **Data** 탭에서 `SELECT version, description FROM flyway_schema_history WHERE success = 0;`
  1. 실패한 스크립트에서 이미 적용된 DDL 을 DB 에서 직접 되돌린다.
  2. 실패 행을 지운다 (`flyway repair` 와 같은 효과): `DELETE FROM flyway_schema_history WHERE success = 0;`
  3. 고친 마이그레이션으로 다시 PR → 머지. 이미 성공 기록된 버전 파일은 고치지 않는다 (체크섬 불일치) — 새 버전 번호로 추가한다.

### 수동 롤백

서비스 **Deployments** → 되돌릴 이전 배포의 **⋮** → **Rollback**. 그 배포의 이미지로 다시 띄운다. 위 마이그레이션 호환 규칙이 그대로 적용된다.
