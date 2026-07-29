# 시크릿 교체 체크리스트 (수동 실행용)
#
# 이 파일은 안내만 담습니다. DB/서버에 접속해 비밀번호를 바꾸는 명령은
# 직접 실행하지 마세요. 로컬/운영에서 직접 수행하세요.
#
# 코드/설정 전환(환경변수 필수화)은 이미 저장소에 반영되어 있습니다.

## a. 새 DB 비밀번호 생성
```bash
openssl rand -base64 32 | tr -d '\n'; echo
```
안전한 비밀번호를 기록해 두세요 (비밀번호 관리자 권장).

## b. PostgreSQL 에서 사용자 비밀번호 변경 (형태만 — 실행은 직접)
슈퍼유저로 접속한 뒤:
```sql
ALTER USER your_db_user WITH PASSWORD '여기에_새_비밀번호';
```
필요 시 사용자/DB 를 새로 만들 때:
```sql
CREATE DATABASE stockknockdb;
CREATE USER your_db_user WITH PASSWORD '여기에_새_비밀번호';
GRANT ALL PRIVILEGES ON DATABASE stockknockdb TO your_db_user;
```

## c. 새 JWT 시크릿 생성
```bash
openssl rand -base64 64 | tr -d '\n'; echo
```
(64 bytes 이상 권장. JwtUtil 이 길이 검증함)

## d. 로컬 .env 에 반영
```bash
cp knockBE/.env.example knockBE/.env
cp knockAI/.env.example knockAI/.env
```
편집 예:
- `knockBE/.env`: `DB_USERNAME`, `DB_PASSWORD`, `DB_URL`, `SKJWT_SECRET`, `OPENAI_API_KEY`
- `knockAI/.env`: `DATABASE_URL` (새 비밀번호 포함), `OPENAI_API_KEY`

실행 전:
```bash
# Backend
cd knockBE && set -a && source .env && set +a && ./gradlew bootRun

# FastAPI (python-dotenv 가 .env 자동 로드)
cd knockAI && source venv/bin/activate && uvicorn app.main:app --reload
```

## e. 스테이징/운영이 있다면
해당 환경의 시크릿 저장소(환경변수, Secret Manager, CI secrets 등)에서
`DB_PASSWORD` / `DB_USERNAME` / `DB_URL` / `DATABASE_URL` / `SKJWT_SECRET` 을
새 값으로 갱신한 뒤 서비스를 재기동하세요.

## f. JWT 로테이션 영향
기존 `SKJWT_SECRET`(또는 예전 하드코딩 기본값)으로 발급된 토큰은
모두 검증 실패합니다. 사용자에게 재로그인을 안내하세요.
(클라이언트 localStorage 의 token 삭제 또는 자연 만료)

## g. Git history rewrite
과거 커밋에 옛 DB 비밀번호·JWT 기본값이 남아 있습니다.
`git filter-repo` 등으로 history 를 정리할지 여부는 별도로 결정하세요.
(공개 저장소면 rewrite + force-push + 원격 캐시 무효화까지 검토)

이 항목은 이 작업에서 자동 진행하지 않습니다. 필요하면 다시 지시해 주세요.
