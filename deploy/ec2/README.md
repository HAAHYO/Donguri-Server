# EC2 배포

`main` 에 머지되면 CD(`.github/workflows/cd.yml`)가 이미지를 GHCR 에 푸시하고, `deploy` job 이 SSH 로 EC2 에 접속해 [`deploy.sh`](deploy.sh) 를 실행한다.

```
PR            → CI(check) + CD image job(이미지 빌드 → MySQL 과 함께 prod 프로필로 기동 → 헬스체크)   ※ 푸시·배포 없음
main 머지     → CD image job(check → 빌드 → 스모크 → GHCR 푸시) → deploy job(EC2 교체 → 헬스체크 → 실패 시 롤백)
Actions 수동 실행(main) → 위와 같음 (재배포)
```

아래 준비는 **처음 한 번**만 한다. 준비 전에 `main` 에 머지하면 deploy job 은 "Secrets 가 없습니다" 로 실패하고, 이미지 푸시는 정상 완료된다.

## 1. EC2 인스턴스

- AMI: Amazon Linux 2023, 타입: `t3.small` 이상 (JVM 메모리 고려. 같은 인스턴스에 MySQL 도 띄우면 `t3.medium` 권장)
- 키 페어: 관리자 접속용. 배포용 키는 아래 3단계에서 따로 만든다.
- 보안 그룹 인바운드:
  | 포트 | 소스 | 용도 |
  |------|------|------|
  | 22 | 내 IP + GitHub Actions | 관리 접속, 배포 |
  | 80 | 서비스 대상 (공개면 0.0.0.0/0) | API |

  GitHub Actions 러너 IP 는 범위가 넓고 바뀐다 (`https://api.github.com/meta` 의 `actions`). 사실상 22 를 넓게 열게 되므로 **비밀번호 로그인은 꺼 두고(AL2023 기본값) 키 인증만** 쓴다.

## 2. Docker 설치

```bash
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user   # 다시 로그인해야 적용
```

## 3. 배포용 SSH 키

로컬에서 배포 전용 키를 만든다 (비밀번호 없이 — Actions 가 사용).

```bash
ssh-keygen -t ed25519 -N "" -C donguri-deploy -f donguri-deploy
```

`donguri-deploy.pub` 내용을 EC2 의 `~ec2-user/.ssh/authorized_keys` 에 한 줄 추가한다.

## 4. 운영 설정 파일 `/opt/donguri/.env`

비밀값은 이 파일에만 둔다 (GitHub 에 올리지 않는다).

```bash
sudo mkdir -p /opt/donguri
sudo tee /opt/donguri/.env >/dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<DB 호스트>:3306/fds_bank?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&sessionVariables=innodb_lock_wait_timeout=3
DB_USERNAME=<사용자>
DB_PASSWORD=<비밀번호>
JWT_SECRET=<openssl rand -base64 32 결과>
EOF
sudo chown ec2-user: /opt/donguri/.env
sudo chmod 600 /opt/donguri/.env
```

- 소유자는 배포 사용자(`EC2_USER`)여야 한다. `docker run --env-file` 은 dockerd 가 아니라 docker 명령을 실행한 사용자가 파일을 읽는다 — root 소유 600 이면 매 배포가 `permission denied` 로 실패한다.

- `DB_URL` 의 `sessionVariables=innodb_lock_wait_timeout=3` 는 빼지 않는다. 없으면 락 대기가 50초가 된다.
- 값에 따옴표를 넣지 않는다 (`--env-file` 은 따옴표를 값의 일부로 읽는다).
- DB 는 RDS 또는 같은 EC2 의 MySQL 8.0. 같은 EC2 라면 `<DB 호스트>` 는 컨테이너 기준이라 `localhost` 가 아니다 — `172.17.0.1`(docker0) 을 쓰고 MySQL 이 그 주소에서 접속을 받도록 설정한다.
- 스키마는 앱 기동 시 Flyway 가 만든다. 빈 DB 를 미리 만들어 둔다.

## 5. GitHub 설정

저장소 → Settings → Environments → `production` (첫 배포 실행 시 자동 생성되며, 미리 만들어도 된다) → Environment secrets:

| 이름 | 값 |
|------|----|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 DNS (탄력적 IP 권장 — 재시작해도 안 바뀜) |
| `EC2_USER` | `ec2-user` |
| `EC2_SSH_KEY` | 3단계 `donguri-deploy` 개인키 전체 (`-----BEGIN ...` 부터 끝까지) |
| `EC2_KNOWN_HOSTS` | 로컬에서 `ssh-keyscan -t ed25519 <EC2_HOST>` 출력 |

`EC2_KNOWN_HOSTS` 는 EC2 의 호스트 키를 고정해 중간자 공격을 막는다. 인스턴스를 새로 만들면 다시 등록한다.

GHCR 이미지 pull 은 deploy job 의 임시 토큰으로 로그인하므로 별도 Secret 이 필요 없다.

배포 전 승인을 걸고 싶으면 같은 화면의 **Required reviewers** 를 켠다.

## 6. 첫 배포와 확인

1. Actions → CD → **Run workflow** (branch: `main`) 로 실행하거나, 다음 릴리스 PR 을 머지한다.
2. deploy job 로그에서 `DEPLOY OK: ghcr.io/haahyo/donguri-server:sha-xxxxxxx` 를 확인한다.
3. `curl http://<EC2_HOST>/actuator/health` → `{"status":"UP"}`

## 문제가 생겼을 때

| 증상 (deploy job 로그) | 원인과 조치 |
|------------------------|-------------|
| `Secrets 가 없습니다: ...` | 5단계에서 빠진 이름을 등록 |
| `Host key verification failed` | `EC2_KNOWN_HOSTS` 가 비었거나 인스턴스가 바뀜 → 다시 `ssh-keyscan` |
| `Permission denied (publickey)` | `authorized_keys` 에 공개키가 없거나 `EC2_USER` 가 다름 |
| `Connection timed out` | 보안 그룹 22 포트 |
| `DEPLOY ERROR: /opt/donguri/.env 가 없거나 ec2-user 가 읽을 수 없습니다` | 4단계 — 파일 생성과 `chown ec2-user:` |
| `DEPLOY FAILED ... ROLLBACK OK` | 새 이미지가 기동하지 못함. 로그의 컨테이너 출력(대개 DB 접속, Flyway, 환경변수)을 보고 고친 뒤 다시 PR. 서비스는 직전 이미지로 동작 중 — 단, 마이그레이션이 적용됐거나 중간에 실패했다면 아래 "롤백과 DB 마이그레이션" 을 확인 |
| `ROLLBACK FAILED` | 직전 이미지도 뜨지 않음 → DB/설정 문제. EC2 에서 `docker logs donguri` |

### 롤백과 DB 마이그레이션

롤백은 **이미지만** 되돌린다. Flyway 가 이미 적용한 마이그레이션은 되돌리지 않는다.

- **마이그레이션은 직전 이미지와 호환되게 쓴다.** 컬럼·테이블 추가는 안전하다 (구 버전 앱은 새 마이그레이션을 미래 버전으로 무시하고, `ddl-auto: validate` 는 엔티티에 있는 컬럼만 확인한다). 단, `NOT NULL` 컬럼은 `DEFAULT` 를 함께 준다 — 없으면 롤백한 구 버전은 뜨지만 그 테이블의 INSERT 가 모두 `doesn't have a default value` 로 실패한다. 컬럼 삭제·이름 변경·타입 축소는 직전 이미지의 `validate` 를 깨므로, 코드에서 먼저 사용을 끊고 다음 릴리스에서 지운다.
- **마이그레이션이 중간에 실패한 경우:** MySQL 의 DDL 은 트랜잭션으로 되돌려지지 않는다. 앞부분 DDL 은 남고 `flyway_schema_history` 에 `success = 0` 행이 생긴다.
  - 직전 이미지는 그 버전을 모르므로 실패 행을 "미래의 실패"로 보고 경고만 남긴 채 뜬다. 그래서 대개 **`ROLLBACK OK` 가 나오지만 스키마는 반쯤 바뀐 상태**다. `ROLLBACK OK` 만 보고 넘어가지 않는다.
  - 고친 이미지를 다시 배포해도 같은 버전의 실패 행 때문에 검증에서 막힌다 (`ROLLBACK OK` 가 반복된다).
  - 확인: deploy job 로그의 컨테이너 출력에서 `Migration of schema` / `failed` 를 찾거나, DB 에서 `SELECT version, description FROM flyway_schema_history WHERE success = 0;`
  1. 실패한 스크립트에서 이미 적용된 DDL 을 DB 에서 직접 되돌린다.
  2. 실패 행을 지운다 (`flyway repair` 와 같은 효과): `DELETE FROM flyway_schema_history WHERE success = 0;`
  3. 고친 마이그레이션으로 다시 PR → 머지. 이미 성공 기록된 버전 파일은 고치지 않는다 (체크섬 불일치) — 새 버전 번호로 추가한다.

### 수동 롤백

- **직전 이미지로:** EC2 에는 현재·직전 이미지가 남아 있어(나머지는 배포 성공 시 정리) GHCR 로그인 없이 바로 된다.
  ```bash
  docker inspect --format '{{.Config.Image}}' donguri   # 현재 이미지
  docker images ghcr.io/haahyo/donguri-server           # 남아 있는 이미지
  bash deploy.sh ghcr.io/haahyo/donguri-server:sha-<직전 커밋>
  ```
  `deploy.sh` 는 저장소의 [`deploy/ec2/deploy.sh`](deploy.sh) 를 EC2 에 복사해 두고 쓴다.
- **더 오래된 이미지로:** GHCR 에는 모든 `sha-*` 태그가 남는다. Actions 에서 그 커밋의 CD 실행을 열고 **deploy job 하나만** Re-run 한다 (그 커밋의 `sha-*` 이미지를 배포한다).
  **Re-run all jobs 는 쓰지 않는다** — image job 이 다시 돌면서 GHCR 의 `main` 태그를 옛 이미지로 덮어쓴다.
