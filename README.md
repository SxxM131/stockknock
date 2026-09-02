# StocKKnock

국내·해외 주식 투자자를 위한 AI 기반 통합 주식 분석 플랫폼입니다. 실시간 시세, AI 시장 브리핑, 포트폴리오 관리, 가격 알림을 한곳에서 제공합니다.

| 항목 | 내용 |
|------|------|
| **상태** | 개발 중 |
| **유형** | 개인 프로젝트 (1인 풀스택) |

---

## 소개

StocKKnock은 보유 주식 관리부터 GPT 기반 시장 분석, 뉴스·유튜버 영상 요약까지 투자에 필요한 정보를 통합한 웹 서비스입니다. Spring Boot가 인증·비즈니스 로직을 담당하고, FastAPI가 AI·데이터 분석을 처리하는 하이브리드 구조로 설계했습니다.

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| **실시간 주가** | Yahoo Finance 등 외부 API 연동, 시장 개장 시간(평일 09:00–15:30)에만 1분마다 갱신 |
| **오늘의 시장 브리핑** | GPT 일일 시장 요약 (평일 1회 생성, DB 캐시) |
| **유튜버 브리핑** | 화이트리스트 채널 신규 영상 수집 → GPT 요약 (핵심 전망 / 언급 종목 / 투자 톤) |
| **포트폴리오** | 보유 종목 관리, 손익 계산, AI 종합 분석 (해시 기반 캐싱으로 재분석 방지) |
| **가격 알림** | 목표가·손절가·변동률 기준 알림, 30초마다 조건 체크 |
| **AI 채팅** | 문맥 유지 대화형 AI 애널리스트 (최근 5개 대화 기반) |
| **뉴스 피드** | 최근 7일 뉴스, 종목별 필터, 뉴스·유튜버 브리핑 탭 |
| **관심 종목** | 사용자별 관심 종목 추가·삭제 |

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| **Frontend** (`knockFE`) | React 19, TypeScript, Vite, TanStack Query, Recharts |
| **Backend** (`knockBE`) | Spring Boot, Java 17, Spring Security, JWT, JPA |
| **AI Service** (`knockAI`) | Python, FastAPI, OpenAI, APScheduler, yfinance, pandas |
| **Database** | PostgreSQL (로컬 또는 Supabase) |
| **외부 API** | Yahoo Finance, Alpha Vantage, Twelve Data, YouTube Data API v3 |

---

## 시스템 구조

```
React (knockFE)     Spring Boot (knockBE)      PostgreSQL
localhost:5173 ──▶  localhost:8080       ──▶  DB
                         │
                         └── 내부 호출 ──▶  FastAPI (knockAI)
                                           localhost:8000
```

> 프론트엔드는 Spring Boot만 호출합니다. FastAPI는 백엔드에서 내부적으로 호출합니다.

---

## 프로젝트 구조

```
stockknock/
├── knockFE/        # 프론트엔드 (React + Vite)
├── knockBE/        # 백엔드 (Spring Boot) — 인증, 포트폴리오, 알림
├── knockAI/        # AI 서비스 (FastAPI) — GPT, 뉴스, 스케줄러
├── quick_start.sh  # 빠른 시작 스크립트
├── ARCHITECTURE.md # 아키텍처 상세
└── FORDEV.md       # 개발 가이드
```

---

## 시작하기

### 사전 요구사항

- Node.js 18+
- Java 17+
- Python 3.9+
- PostgreSQL
- OpenAI API Key

### 1. 데이터베이스

```bash
psql postgres
CREATE DATABASE stockknockdb;
CREATE USER your_db_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE stockknockdb TO your_db_user;
```

### 2. 환경 변수

```bash
cp knockBE/.env.example knockBE/.env
cp knockAI/.env.example knockAI/.env
# 각 파일에 DB, JWT, OpenAI 키 입력
```

### 3. 빠른 시작 (권장)

```bash
chmod +x quick_start.sh
./quick_start.sh
```

### 4. 수동 실행

**터미널 1 — 백엔드**

```bash
cd knockBE
set -a && source .env && set +a && ./gradlew bootRun
```

**터미널 2 — AI 서비스**

```bash
cd knockAI
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

**터미널 3 — 프론트엔드**

```bash
cd knockFE
npm install && npm run dev
```

| 서비스 | URL |
|--------|-----|
| 프론트엔드 | `http://localhost:5173` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| FastAPI | `http://localhost:8000` |

### 환경 변수 (knockBE)

| 변수 | 필수 | 설명 |
|------|------|------|
| `DB_URL` | ✅ | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | ✅ | DB 인증 |
| `SKJWT_SECRET` | ✅ | JWT 서명 키 (64 bytes 이상) |
| `OPENAI_API_KEY` | ✅ | OpenAI API 키 |
| `FASTAPI_BASE_URL` | ✅ | FastAPI URL (`http://localhost:8000`) |
| `YOUTUBE_API_KEY` | | YouTube Data API (유튜버 브리핑) |
| `ALPHA_VANTAGE_API_KEY` | | 주가 API (선택) |
| `TWELVE_DATA_API_KEY` | | 주가 API (선택) |

> 상세: `knockBE/.env.example`, `knockAI/.env.example` 참고

---

## 상세 문서

| 문서 | 내용 |
|------|------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Spring + FastAPI 하이브리드 아키텍처 |
| [FORDEV.md](./FORDEV.md) | API 엔드포인트, DB 스키마, 개발 가이드 |
| [SECRET_ROTATION_CHECKLIST.md](./SECRET_ROTATION_CHECKLIST.md) | 시크릿 로테이션 체크리스트 |

---

## 참고

- 본 서비스는 투자 조언을 제공하지 않습니다. AI 분석 결과는 참고용이며, 투자 결정은 본인 책임입니다.
- API 호출 비용 절감을 위해 시장 개장 시간 갱신, DB 캐시, 해시 기반 AI 재분석 방지를 적용했습니다.
