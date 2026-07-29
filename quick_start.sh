#!/bin/bash

echo "========================================="
echo "  StocKKnock 빠른 시작 스크립트"
echo "========================================="
echo ""

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

# .env 로드 (프로젝트 루트 → knockBE → knockAI 순)
load_env_file() {
    local env_file="$1"
    if [ -f "$env_file" ]; then
        set -a
        # shellcheck disable=SC1090
        source "$env_file"
        set +a
        echo -e "${GREEN}✓ 환경 파일 로드: ${env_file}${NC}"
        return 0
    fi
    return 1
}

load_env_file "$ROOT_DIR/.env" || true
load_env_file "$ROOT_DIR/knockBE/.env" || true
load_env_file "$ROOT_DIR/knockAI/.env" || true

# PostgreSQL 연결에 필요한 값 (하드코딩 금지)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-stockknockdb}"
DB_USERNAME="${DB_USERNAME:-}"
DB_PASSWORD="${DB_PASSWORD:-}"

if [ -z "$DB_USERNAME" ]; then
    read -r -p "DB 사용자명 (DB_USERNAME): " DB_USERNAME
fi
if [ -z "$DB_PASSWORD" ]; then
    read -r -s -p "DB 비밀번호 (DB_PASSWORD, 입력 숨김): " DB_PASSWORD
    echo ""
fi

if [ -z "$DB_USERNAME" ] || [ -z "$DB_PASSWORD" ]; then
    echo -e "${RED}✗ DB_USERNAME / DB_PASSWORD 가 필요합니다.${NC}"
    echo "knockBE/.env.example 을 복사해 .env 를 만들거나, 실행 시 입력하세요."
    exit 1
fi

# PostgreSQL 확인
echo -e "${YELLOW}[1/5] PostgreSQL 연결 확인 중...${NC}"
if PGPASSWORD="$DB_PASSWORD" psql -U "$DB_USERNAME" -d "$DB_NAME" -h "$DB_HOST" -p "$DB_PORT" -c "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ PostgreSQL 연결 성공${NC}"
else
    echo -e "${RED}✗ PostgreSQL 연결 실패${NC}"
    echo "데이터베이스를 먼저 생성해주세요 (비밀번호는 직접 설정한 안전한 값을 사용하세요):"
    echo "  psql postgres"
    echo "  CREATE DATABASE stockknockdb;"
    echo "  CREATE USER your_db_user WITH PASSWORD 'your_db_password_here';"
    echo "  GRANT ALL PRIVILEGES ON DATABASE stockknockdb TO your_db_user;"
    echo "그 다음 knockBE/.env 와 knockAI/.env 에 DB_USERNAME / DB_PASSWORD / DATABASE_URL 을 설정하세요."
    echo "자세한 내용은 README.md를 참고하세요."
    exit 1
fi

# 백엔드 빌드
echo -e "${YELLOW}[2/5] 백엔드 빌드 중...${NC}"
cd "$ROOT_DIR/knockBE"
chmod +x gradlew 2>/dev/null
if ./gradlew clean build -x test > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 백엔드 빌드 성공${NC}"
else
    echo -e "${RED}✗ 백엔드 빌드 실패${NC}"
    echo "상세 로그를 확인하려면: cd knockBE && ./gradlew clean build"
    exit 1
fi
cd "$ROOT_DIR"

# 프론트엔드 의존성 확인
echo -e "${YELLOW}[3/5] 프론트엔드 의존성 확인 중...${NC}"
cd "$ROOT_DIR/knockFE"
if [ ! -d "node_modules" ]; then
    echo "의존성 설치 중..."
    npm install > /dev/null 2>&1
fi
echo -e "${GREEN}✓ 프론트엔드 준비 완료${NC}"
cd "$ROOT_DIR"

# FastAPI 의존성 확인
echo -e "${YELLOW}[4/5] FastAPI 의존성 확인 중...${NC}"
cd "$ROOT_DIR/knockAI"
if [ ! -d "venv" ]; then
    echo "가상 환경 생성 중..."
    python3 -m venv venv > /dev/null 2>&1
fi
if [ ! -f "venv/bin/activate" ]; then
    echo -e "${RED}✗ FastAPI 가상 환경 생성 실패${NC}"
    exit 1
fi
echo -e "${GREEN}✓ FastAPI 준비 완료${NC}"
cd "$ROOT_DIR"

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}  준비 완료!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "실행 전 환경 변수가 로드되어 있는지 확인하세요 (SKJWT_SECRET, DB_*, DATABASE_URL, OPENAI_API_KEY)."
echo ""
echo "1. 백엔드 실행 (터미널 1):"
echo "   ${YELLOW}cd knockBE && set -a && source .env && set +a && ./gradlew bootRun${NC}"
echo ""
echo "2. FastAPI 실행 (터미널 2):"
echo "   ${YELLOW}cd knockAI && source venv/bin/activate && uvicorn app.main:app --reload${NC}"
echo ""
echo "3. 프론트엔드 실행 (터미널 3):"
echo "   ${YELLOW}cd knockFE && npm run dev${NC}"
echo ""
echo "4. 브라우저에서 접속:"
echo "   ${YELLOW}http://localhost:5173${NC} (또는 Vite가 표시한 포트)"
echo ""
echo "5. Swagger UI 접속:"
echo "   ${YELLOW}http://localhost:8080/swagger-ui.html${NC}"
echo ""
echo "자세한 내용은 README.md를 참고하세요."
