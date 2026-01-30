# StocKKnock 개발자 가이드 (FORDEV.md)

이 문서는 StocKKnock 프로젝트의 기능별 상세 구현 상황을 설명합니다. 프로젝트에 처음 참여하는 개발자도 이 문서를 읽고 바로 구현 구조와 방법을 이해할 수 있도록 작성되었습니다.

---

## 📋 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [아키텍처 구조](#아키텍처-구조)
3. [핵심 기능별 구현 상세](#핵심-기능별-구현-상세)
4. [데이터베이스 설계](#데이터베이스-설계)
5. [API 설계 원칙](#api-설계-원칙)
6. [AI 서비스 통합](#ai-서비스-통합)
7. [캐싱 전략](#캐싱-전략)
8. [에러 처리 및 로깅](#에러-처리-및-로깅)
9. [개발 환경 설정](#개발-환경-설정)
10. [새로운 기능 추가 가이드](#새로운-기능-추가-가이드)

---

## 프로젝트 개요

### 기술 스택

- **Backend**: Spring Boot 3.3.5 (Java 17)
- **Frontend**: React 19 (TypeScript, Vite)
- **Database**: PostgreSQL 12+
- **AI**: OpenAI GPT-4o-mini API
- **빌드 도구**: Gradle (Backend), npm (Frontend)

### 프로젝트 구조

```
stockknock/
├── knockBE/              # Spring Boot Backend
│   ├── src/main/java/com/sxxm/stockknock/
│   │   ├── auth/         # 인증 및 사용자 관리
│   │   ├── portfolio/    # 포트폴리오 관리
│   │   ├── stock/        # 주식 정보 관리
│   │   ├── news/         # 뉴스 및 시장 브리핑
│   │   ├── ai/           # AI 서비스 (GPT 통합)
│   │   ├── watchlist/    # 관심 종목
│   │   ├── alert/        # 가격 알림
│   │   └── common/       # 공통 유틸리티
│   └── src/main/resources/
│       └── db/
│           └── schema.sql  # 데이터베이스 스키마
│
├── knockFE/              # React Frontend
│   └── src/
│       ├── pages/        # 페이지 컴포넌트
│       ├── api/          # API 클라이언트
│       └── context/        # Context API
│
└── knockAI/              # FastAPI (내부 서비스, 현재 미사용)
```

---

## 아키텍처 구조

### 전체 흐름

```
Frontend (React)
    ↓ HTTP/REST
Spring Boot (Port 8080)
    ↓ (내부 호출 또는 직접 호출)
    ├─→ GPT API (OpenAI) - AI 채팅, 포트폴리오 분석, 시장 브리핑
    ├─→ FastAPI (Port 8000) - 주가 조회, 뉴스 수집 (폴백)
    └─→ PostgreSQL (공유 데이터베이스)
```

### 핵심 원칙

1. **프론트엔드는 오직 Spring Boot API만 호출**
   - FastAPI는 백엔드 내부 서비스로만 사용
   - 모든 보안, 로깅, 제한은 Spring Boot에서 중앙 관리

2. **AI 서비스는 백엔드에서 직접 GPT API 호출**
   - `GPTClientService`를 통한 통일된 GPT 호출
   - 비동기 처리 및 타임아웃 관리 (90초)
   - FastAPI는 폴백으로만 사용

3. **캐싱 전략으로 비용 최적화**
   - 포트폴리오 분석: 구성 변경 시에만 재분석
   - 시장 브리핑: 하루 1회 생성, 전역 공유

---

## 핵심 기능별 구현 상세

### 1. 사용자 인증 및 계정 관리

#### 구현 위치
- **Controller**: `auth/controller/AuthController.java`
- **Service**: `auth/service/UserService.java`, `auth/service/EmailVerificationService.java`
- **Entity**: `auth/entity/User.java`, `auth/entity/EmailVerification.java`

#### 주요 기능

##### 1.1 회원가입 및 로그인
- **엔드포인트**: `POST /api/auth/register`, `POST /api/auth/login`
- **인증 방식**: 아이디(username) 기반 (이메일과 별도)
- **JWT 토큰**: 로그인 성공 시 발급, 모든 API 요청에 `Authorization: Bearer {token}` 헤더 필요

**구현 세부사항**:
```java
// UserService.java
public AuthResponse register(String username, String email, String password, String nickname)
public AuthResponse login(String username, String password)
```

- 비밀번호는 BCrypt로 암호화 저장
- JWT 토큰은 `JwtUtil`을 통해 생성/검증
- 토큰 만료 시간: 24시간 (설정 가능)

##### 1.2 마이페이지 - 개인정보 관리

**아이디 변경** (`PUT /api/auth/username`):
- 현재 비밀번호 확인 필요
- 중복 아이디 체크
- 변경 후 재로그인 필요

**닉네임 변경** (`PUT /api/auth/profile`):
- 비밀번호 확인 불필요
- 즉시 반영

**이메일 변경** (`PUT /api/auth/email`):
- **2단계 인증 프로세스**:
  1. `POST /api/auth/email/verification-code`: 새 이메일로 6자리 인증 코드 전송
  2. `PUT /api/auth/email`: 인증 코드 + 현재 비밀번호 확인 후 이메일 변경
- 인증 코드는 10분간만 유효
- 만료된 코드는 스케줄러로 자동 정리

**비밀번호 변경** (`PUT /api/auth/password`):
- 현재 비밀번호 확인 필요
- 최소 6자 이상

#### 데이터베이스 스키마

```sql
-- users 테이블
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,  -- 로그인 ID
    email VARCHAR(255) UNIQUE NOT NULL,     -- 이메일
    password VARCHAR(255) NOT NULL,         -- BCrypt 암호화
    nickname VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- email_verification 테이블 (이메일 변경용)
CREATE TABLE email_verification (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    verification_code VARCHAR(6) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,  -- 생성 후 10분
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### 2. 포트폴리오 관리

#### 구현 위치
- **Controller**: `portfolio/controller/PortfolioController.java`
- **Service**: `portfolio/service/PortfolioService.java`, `portfolio/service/PortfolioAnalysisService.java`
- **Entity**: `portfolio/entity/Portfolio.java`, `portfolio/entity/PortfolioItem.java`, `portfolio/entity/PortfolioAnalysis.java`

#### 주요 기능

##### 2.1 포트폴리오 CRUD

**포트폴리오 조회** (`GET /api/portfolio`):
- 사용자의 모든 포트폴리오 항목 조회
- 실시간 가격 조회 및 손익 계산 포함
- **N+1 Query 방지**: 모든 종목의 가격을 한 번에 조회 (`getCurrentPricesBatch`)

**포트폴리오 항목 추가** (`POST /api/portfolio`):
- Request Body: `{ stockSymbol, quantity, avgBuyPrice }`
- 기존 종목이면 수량과 평균가 재계산 (가중 평균)
- 새 종목이면 `PortfolioItem` 생성
- 포트폴리오 변경 시 AI 분석 캐시 무효화

**포트폴리오 항목 수정** (`PUT /api/portfolio/{itemId}`):
- 수량 또는 평균가 수정 가능
- 포트폴리오 변경 시 AI 분석 캐시 무효화

**포트폴리오 항목 삭제** (`DELETE /api/portfolio/{itemId}`):
- 포트폴리오 변경 시 AI 분석 캐시 무효화

##### 2.2 AI 포트폴리오 분석 (캐싱 최적화)

**엔드포인트**: `GET /api/portfolio/analysis?forceRefresh={boolean}`

**구현 세부사항**:

1. **포트폴리오 해시 생성** (`PortfolioHashUtil.generateHash`):
   - 종목 심볼, 수량, 평균 매입가를 기준으로 SHA-256 해시 생성
   - 정렬 후 문자열로 변환하여 일관된 해시 보장
   - 같은 구성 → 같은 해시

2. **캐싱 로직** (`PortfolioAnalysisService.getPortfolioAnalysis`):
   ```java
   // 1. 현재 포트폴리오 → hash 계산
   String currentHash = PortfolioHashUtil.generateHash(items);
   
   // 2. DB에서 기존 분석 조회
   Optional<PortfolioAnalysis> existing = portfolioAnalysisRepository.findByUserId(userId);
   
   // 3. 캐시 확인
   if (existing.isPresent() && !forceRefresh) {
       if (currentHash.equals(existing.get().getPortfolioHash()) && 
           existing.get().getStatus() == AnalysisStatus.SUCCESS) {
           // 캐시 사용
           return buildDtoFromCache(existing.get(), items);
       }
   }
   
   // 4. AI 재분석 수행
   return performAnalysis(userId, items, currentHash, existing);
   ```

3. **AI 재분석 트리거 조건**:
   - 포트폴리오 구성 변경 (종목 추가/삭제/수량 변경/평균가 변경)
   - `forceRefresh=true` 파라미터
   - 기존 분석이 없거나 실패 상태인 경우

4. **GPT 프롬프트**:
   - System Prompt: 객관적이고 중립적인 리포트 톤 유지
   - User Prompt: 포트폴리오 데이터 (종목별 보유량, 평균가, 현재가, 손익)
   - 출력 형식: 포트폴리오 해석 (1~2문장) + 핵심 리스크 (1~2문장) + 고려해볼 선택지 (불릿 2~3개)

5. **결과 저장**:
   - `portfolio_analysis` 테이블에 저장 (사용자당 1개만 유지)
   - `status`: SUCCESS 또는 FAILED
   - 실패 시에도 기본 메시지 저장하여 재시도 가능

#### 데이터베이스 스키마

```sql
-- portfolios 테이블
CREATE TABLE portfolios (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(255) DEFAULT 'Default Portfolio',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- portfolio_items 테이블
CREATE TABLE portfolio_items (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES portfolios(id),
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol),
    quantity NUMERIC(18,4) NOT NULL,
    avg_buy_price NUMERIC(18,4) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- portfolio_analysis 테이블 (AI 분석 결과 캐싱)
CREATE TABLE portfolio_analysis (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    portfolio_hash VARCHAR(64) NOT NULL,  -- SHA-256 해시
    analysis_content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

### 3. 주식 정보 관리

#### 구현 위치
- **Controller**: `stock/controller/StockController.java`
- **Service**: `stock/service/StockService.java`, `stock/service/StockPriceService.java`
- **Entity**: `stock/entity/Stock.java`, `stock/entity/StockPriceHistory.java`

#### 주요 기능

##### 3.1 주식 정보 조회

**심볼로 조회** (`GET /api/stocks/symbol/{symbol}`):
- 종목 코드로 주식 정보 조회
- 현재가, 전일 종가, 고가/저가, 거래량 포함

**검색** (`GET /api/stocks/search?keyword={keyword}`):
- 종목명 또는 심볼로 검색

##### 3.2 주가 조회 및 업데이트

**현재가 조회** (`StockService.getCurrentPrice`):
1. DB에서 최신 가격 조회 (`stock_price_history` 테이블)
2. 시장 시간 체크 (`isMarketHours()`):
   - 시장 개장 시간 (평일 09:00-15:30 KST)이면 DB 가격만 반환
   - 시장 휴장 시간이면 외부 API 호출 가능
3. 외부 API 호출 (시장 휴장 시간 또는 DB에 없을 때):
   - Yahoo Finance (1순위, 무료)
   - Alpha Vantage (2순위, API 키 필요)
   - Twelve Data (3순위, API 키 필요)
4. 가격 조회 실패 시 `null` 반환 (0 대신)

**배치 가격 조회** (`StockService.getCurrentPricesBatch`):
- N+1 Query 방지: 여러 종목의 가격을 한 번에 조회
- `IN (:symbols)` 쿼리로 최신 가격 조회
- 결과를 `Map<String, BigDecimal>`로 반환

**주가 업데이트 스케줄러** (`StockPriceScheduler`):
- **장 마감 후 일일 업데이트**: 평일 16:00-23:00, 매 시간 정각에 체크 (`@Scheduled(cron = "0 0 16-23 * * MON-FRI")`)
- **실행 조건**:
  - 평일만 실행 (토요일, 일요일 제외)
  - 장 마감 후 (15:30 이후)
  - 하루 1회만 실행 (중복 방지: `lastUpdateDate` 체크)
- **시장 개장 시간**: 가격 조회는 비활성화 (캐시만 사용)
- **시장 휴장 시간**: 외부 API 호출 가능

#### 데이터베이스 스키마

```sql
-- stocks 테이블 (종목 코드가 기본 키)
CREATE TABLE stocks (
    symbol VARCHAR(20) PRIMARY KEY,  -- 종목 코드 (예: AAPL, 005930)
    name VARCHAR(255) NOT NULL,
    exchange VARCHAR(50),              -- NYSE, NASDAQ, KOSPI, KOSDAQ
    country VARCHAR(50),              -- US, KR
    industry VARCHAR(100),
    currency VARCHAR(10),             -- USD, KRW
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- stock_price_history 테이블
CREATE TABLE stock_price_history (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol),
    price NUMERIC(18,4) NOT NULL,
    open NUMERIC(18,4),
    high NUMERIC(18,4),
    low NUMERIC(18,4),
    volume BIGINT,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(stock_symbol, timestamp)
);
```

---

### 4. AI 서비스 통합

#### 구현 위치
- **GPTClientService**: `ai/service/GPTClientService.java` - GPT API 호출 전담
- **AIChatService**: `ai/service/AIChatService.java` - AI 채팅 전용 로직
- **AIService**: `ai/service/AIService.java` - @Deprecated (하위 호환성)

#### 주요 기능

##### 4.1 GPTClientService (기준 API)

**역할**: 모든 GPT 호출의 기준 API
- 비동기 처리 (`Mono<String>` 반환)
- 타임아웃 관리 (90초)
- 예외 처리 (SocketTimeoutException, IOException 개별 처리)
- 결과 객체 반환 (`AIResponseResult`)

**주요 메서드**:
```java
public Mono<AIResponseResult> generateResponseAsync(String prompt, AIRequestOptions options)
```

**사용 예시**:
```java
AIRequestOptions options = AIRequestOptions.builder()
    .systemPrompt("너는 AI 주식 분석가다.")
    .temperature(0.7)
    .maxTokens(1000)
    .timeoutSeconds(90)
    .build();

AIResponseResult result = gptClientService.generateResponseAsync(prompt, options)
    .block(Duration.ofSeconds(90));

if (result.isSuccess()) {
    String content = result.getContent();
} else {
    String errorMessage = result.getErrorMessage();
}
```

##### 4.2 AIChatService (AI 채팅)

**엔드포인트**: `POST /api/ai/chat`

**⚠️ 중요**: FastAPI를 사용하지 않습니다. 백엔드에서 직접 GPT API를 호출합니다.

**구현 세부사항**:

1. **대화 기록 조회** (`AIController.chat`):
   - `AIConversationRepository.findByUserIdOrderByCreatedAtDesc(userId)`로 최근 대화 기록 조회
   - 최근 5개 대화만 사용 (`.limit(5)`)
   - 시간 역순으로 정렬하여 최신 대화가 마지막에 오도록 구성

2. **대화 문맥 구성**:
   ```java
   // 대화 문맥 구성
   StringBuilder context = new StringBuilder();
   for (int i = recentConversations.size() - 1; i >= 0; i--) {
       AIConversation conv = recentConversations.get(i);
       context.append(conv.getRole()).append(": ").append(conv.getMessage()).append("\n");
   }
   ```
   - 형식: `"user: 질문\nassistant: 답변\nuser: 다음 질문\n..."`
   - 역순으로 순회하여 시간 순서대로 구성

3. **System Prompt 생성** (`AIChatService.buildSystemPrompt`):
   - **실제 System Prompt 전체 내용**:
   ```
   너는 한국 주식 시장을 해석해주는 AI 주식 분석가다.

   중요한 전제:
   - 실시간 뉴스 기사나 데이터에 직접 접근하지는 않는다.
   - 그러나 오늘 날짜 기준으로 일반적으로 논의되는
     시장 흐름, 수급, 투자자 심리를 분석가 관점에서 설명할 수 있다.

   행동 규칙:
   1. "제공할 수 없습니다", "알 수 없습니다" 같은 회피 표현을 사용하지 않는다.
   2. 질문에 '오늘', '현재', '뉴스'가 포함되어도
      시장 해석과 흐름 중심으로 답변한다.
   3. 단정이 어려운 경우에도
      "현재 시장에서는 보통 ○○ 흐름이 나타난다" 형태로 설명한다.
   4. 뉴스 브리핑처럼 요약하지 말고,
      질문에 대한 분석형 설명으로 답한다.
   5. 한국 개인 투자자가 이해하기 쉬운 말투를 사용한다.
   6. 너는 정보 차단을 안내하는 챗봇이 아니라,
      시장을 풀어서 설명해주는 분석가다.
   
   이전 대화 맥락을 고려하되, 현재 질문에 집중해서 답변한다.
   ```
   - 대화 기록이 있으면 (`historyCount > 0`) 마지막 문장 추가

4. **대화 기록 파싱** (`AIChatService.parseConversationHistory`):
   - 문자열 형식의 대화 기록을 `ChatMessage` 리스트로 변환
   - `"사용자: "`로 시작하면 `ChatMessageRole.USER`
   - `"AI: "`로 시작하면 `ChatMessageRole.ASSISTANT`
   - GPT API에 전달할 수 있는 형식으로 변환

5. **GPT 호출** (`AIChatService.answerQuestionWithContextAsync`):
   ```java
   AIRequestOptions options = AIRequestOptions.builder()
       .systemPrompt(systemPrompt)
       .temperature(0.7)
       .maxTokens(1000)
       .conversationHistory(conversationMessages)  // 대화 기록 포함
       .timeoutSeconds(90)
       .build();
   
   return gptClientService.generateResponseAsync(question, options);
   ```
   - 비동기 호출 (`Mono<AIResponseResult>` 반환)
   - 타임아웃: 90초
   - 대화 기록을 포함하여 문맥 유지

6. **FastAPI 폴백 로직** (`AIController.chat`):
   - GPT API 호출 실패 시 FastAPI로 폴백
   ```java
   try {
       AIResponseResult result = aiChatService.answerQuestionWithContextAsync(...)
           .block(Duration.ofSeconds(90));
       
       if (result != null && result.isSuccess()) {
           response = result.getContent();
       } else {
           // GPT 실패 시 FastAPI로 폴백
           response = fastApiService.chatWithAI(...).block();
       }
   } catch (Exception e) {
       // 예외 발생 시 FastAPI로 폴백
       response = fastApiService.chatWithAI(...).block();
   }
   ```

7. **대화 기록 저장**:
   - 사용자 질문 저장: `role = "user"`, `message = request.getQuestion()`
   - AI 응답 저장: `role = "assistant"`, `message = response`
   - `AIConversation` 엔티티로 DB에 저장
   - 이후 대화에서 문맥 유지에 사용

**데이터 흐름**:
```
사용자 질문
  ↓
AIController.chat()
  ↓
최근 5개 대화 기록 조회 (DB)
  ↓
대화 문맥 구성 (문자열)
  ↓
AIChatService.answerQuestionWithContextAsync()
  ↓
System Prompt 생성
  ↓
대화 기록 파싱 (ChatMessage 리스트)
  ↓
GPTClientService.generateResponseAsync() → GPT API 직접 호출
  ↓ (실패 시)
FastApiService.chatWithAI() → FastAPI 폴백
  ↓
AI 응답
  ↓
대화 기록 저장 (DB)
  ↓
사용자에게 응답 반환
```

**에러 처리**:
- GPT API 타임아웃: 90초 초과 시 FastAPI로 폴백
- GPT API 실패: `result.isSuccess() == false` 시 FastAPI로 폴백
- 예외 발생: `catch` 블록에서 FastAPI로 폴백
- FastAPI도 실패하면: 에러 응답 반환 (400 Bad Request)

##### 4.3 PortfolioAnalysisService (포트폴리오 분석)

**엔드포인트**: `GET /api/portfolio/analysis?forceRefresh={boolean}`

**⚠️ 중요**: FastAPI를 사용하지 않습니다. 백엔드에서 직접 GPT API를 호출합니다.

**구현 세부사항**:

1. **포트폴리오 해시 생성** (`PortfolioHashUtil.generateHash`):
   - 종목 심볼, 수량, 평균 매입가를 기준으로 SHA-256 해시 생성
   - 정렬 후 문자열로 변환하여 일관된 해시 보장
   ```java
   String portfolioString = items.stream()
       .sorted(Comparator
           .comparing(item -> item.getStock().getSymbol())
           .thenComparing(PortfolioItem::getQuantity)
           .thenComparing(PortfolioItem::getAvgBuyPrice))
       .map(item -> String.format("%s:%s:%s",
           item.getStock().getSymbol(),
           item.getQuantity().toPlainString(),
           item.getAvgBuyPrice().toPlainString()))
       .collect(Collectors.joining("|"));
   
   // SHA-256 해시 생성
   MessageDigest digest = MessageDigest.getInstance("SHA-256");
   byte[] hash = digest.digest(portfolioString.getBytes(StandardCharsets.UTF_8));
   ```

2. **캐싱 로직** (`PortfolioAnalysisService.getPortfolioAnalysis`):
   - 현재 포트폴리오 해시 계산
   - DB에서 기존 분석 조회 (`portfolio_analysis` 테이블)
   - 해시 동일 + 성공 상태면 캐시 사용
   - 해시 다르거나 실패 상태면 AI 재분석

3. **AI 재분석 수행** (`PortfolioAnalysisService.performAnalysis`):
   - 포트폴리오 데이터 수집 (종목별 보유량, 평균가, 현재가, 손익)
   - 현재 가격 조회 (배치 처리로 N+1 Query 방지)
   - 총 평가액, 총 손익, 총 수익률 계산
   - GPT 프롬프트 생성

4. **GPT 프롬프트** (`PortfolioAnalysisService.getPortfolioSystemPrompt`):
   - **실제 System Prompt 전체 내용**:
   ```
   너는 개인 투자자를 위한 AI 포트폴리오 분석가다.

   중요 규칙:
   1. 수익률, 손익, 비중 수치는 이미 계산된 값을 그대로 해석만 한다.
      (새로운 계산을 시도하지 않는다)
   2. 특정 종목의 매수·매도·손절을 직접 지시하지 않는다.
   3. "추천", "확정", "반드시" 같은 표현을 사용하지 않는다.
   4. 모든 행동 제안은 "고려해볼 수 있다", "검토할 수 있다" 형태로 작성한다.
   5. 분석은 객관적이고 중립적인 리포트 톤을 유지한다.

   출력 형식:
   - 포트폴리오 해석 (1~2문장)
   - 핵심 리스크 또는 구조적 특징 (1~2문장)
   - 투자자가 고려해볼 선택지 (불릿 2~3개)

   주의:
   금융 조언처럼 보일 수 있는 단정적 표현을 피하고,
   시장 흐름과 구조 중심으로 설명한다.
   ```
   - **User Prompt**: 포트폴리오 데이터 (종목별 보유량, 평균가, 현재가, 손익, 총 평가액, 총 손익, 총 수익률)

5. **GPT 호출**:
   ```java
   AIRequestOptions options = AIRequestOptions.builder()
       .systemPrompt(getPortfolioSystemPrompt())
       .temperature(0.7)
       .maxTokens(1000)
       .timeoutSeconds(90)
       .build();
   
   AIResponseResult result = gptClientService.generateResponseAsync(
       buildPortfolioDataPrompt(portfolioDataPrompt.toString()), options)
       .block(Duration.ofSeconds(90));
   ```

6. **결과 저장**:
   - `portfolio_analysis` 테이블에 저장 (사용자당 1개만 유지, UNIQUE 제약)
   - `status`: SUCCESS 또는 FAILED
   - 실패 시에도 기본 메시지 저장하여 재시도 가능

7. **캐시에서 조회** (`PortfolioAnalysisService.buildDtoFromCache`):
   - 캐시된 분석 내용 사용
   - 현재 가격 정보는 다시 계산 (가격은 변동되므로)
   - 총 평가액, 총 손익, 총 수익률 재계산

**데이터 흐름**:
```
사용자 요청 (GET /api/portfolio/analysis)
  ↓
PortfolioAnalysisService.getPortfolioAnalysis()
  ↓
현재 포트폴리오 조회 (DB)
  ↓
포트폴리오 해시 계산
  ↓
기존 분석 조회 (DB)
  ↓
해시 비교
  ↓ (해시 동일 + 성공 상태)
캐시 사용 → 현재 가격만 재계산 → 반환
  ↓ (해시 다름 또는 실패)
AI 재분석 수행
  ↓
포트폴리오 데이터 수집 (종목별 정보)
  ↓
현재 가격 조회 (배치 처리)
  ↓
총 평가액, 총 손익, 총 수익률 계산
  ↓
GPT 프롬프트 생성
  ↓
GPTClientService.generateResponseAsync() → GPT API 직접 호출
  ↓
GPT 분석 결과
  ↓
DB에 저장 (portfolio_analysis 테이블)
  ↓
사용자에게 반환
```

**에러 처리**:
- GPT API 타임아웃: 90초 초과 시 `status = FAILED` 저장, 기본 메시지 반환
- GPT API 실패: `result.isSuccess() == false` 시 `status = FAILED` 저장, 기본 메시지 반환
- 예외 발생: `catch` 블록에서 `status = FAILED` 저장, 기본 메시지 반환
- 포트폴리오 비어있음: 기본 메시지 반환 (`"보유 종목이 없습니다..."`)

##### 4.4 MarketBriefingService (시장 브리핑)

**엔드포인트**: `GET /api/news/market-briefing`

**⚠️ 중요**: 
- **FastAPI를 사용하지 않습니다**. 백엔드에서 직접 GPT API를 호출합니다.
- **뉴스 데이터를 읽어오지 않습니다**. GPT가 학습 데이터 기반으로 요약을 생성합니다.

**구현 세부사항**:

1. **스케줄러 실행** (`MarketBriefingScheduler.generateDailyMarketBriefing`):
   - **실행 시간**: 매일 평일 오전 8시 50분 (`@Scheduled(cron = "0 50 8 * * MON-FRI")`)
   - **실행 조건**:
     - 평일만 실행 (토요일, 일요일 제외)
     - 하루 1회만 생성 (중복 방지: `lastGeneratedDate` 체크)
   - **스케줄러 위치**: `news/scheduler/MarketBriefingScheduler.java`

2. **브리핑 생성 로직** (`MarketBriefingService.generateTodayGlobalBriefing`):
   ```java
   // 1. 오늘 날짜 확인
   LocalDate today = LocalDate.now();
   
   // 2. 기존 브리핑 조회 (user_id = 0, 전역 공유)
   Optional<MarketBriefing> existing = 
       marketBriefingRepository.findByUserIdAndDate(GLOBAL_USER_ID, today);
   
   // 3. 이미 생성되었고 성공 상태면 스킵
   if (existing.isPresent() && existing.get().getStatus() == BriefingStatus.SUCCESS) {
       return true;
   }
   
   // 4. GPT 프롬프트 생성
   String prompt = buildPrompt(today);
   
   // 5. GPT API 호출 (직접 호출, FastAPI 미사용)
   AIResponseResult result = gptClientService.generateResponseAsync(prompt)
       .block(Duration.ofSeconds(90));
   
   // 6. 결과 저장
   if (result.isSuccess()) {
       briefing.setContent(result.getContent());
       briefing.setStatus(BriefingStatus.SUCCESS);
   } else {
       briefing.setStatus(BriefingStatus.FAILED);
   }
   ```

3. **GPT 프롬프트** (`MarketBriefingService.buildPrompt`):
   - **실제 프롬프트 전체 내용**:
   ```
   너는 한국 주식 시장 아침 브리핑을 작성하는 금융 리포터야.

   오늘 날짜(2024-01-15) 기준으로
   한국 주식 시장 상황을 정확히 5줄로 요약해줘.

   작성 규칙:
   1. 각 줄은 한 문장만 작성
   2. 애매한 표현 금지 (예: ~보입니다, ~가능성)
   3. 종목 나열보다 시장 흐름 중심
   4. 투자자가 아침에 바로 읽을 수 있는 톤
   5. 마지막 줄은 오늘 시장을 한 단어로 요약

   반드시 정확히 5줄로만 작성하고
   머리말이나 설명 문장은 쓰지 마.
   ```
   - **주의**: 뉴스 데이터를 제공하지 않음
   - GPT가 학습 데이터 기반으로 요약 생성
   - 날짜만 전달하여 "오늘 날짜 기준"으로 요청

4. **전역 공유**:
   - `user_id = 0` (GLOBAL_USER_ID)로 저장
   - 모든 사용자가 동일한 브리핑 조회
   - 하루 1개만 생성 (UNIQUE 제약: `user_id, date`)

5. **브리핑 조회** (`MarketBriefingService.getTodayGlobalBriefing`):
   - **GPT 호출 없음**: DB에서만 조회
   - 성공 상태(`status = SUCCESS`)인 브리핑만 반환
   - 없으면 기본 메시지: `"오늘의 시장 브리핑을 준비 중입니다. 잠시 후 다시 확인해주세요."`

6. **실패 처리**:
   - GPT 호출 실패 시: `status = FAILED`로 저장, `content = null`
   - 사용자에게는 기본 메시지 반환
   - 다음 스케줄러 실행 시 재시도 가능

**데이터 흐름**:
```
스케줄러 (매일 08:50)
  ↓
MarketBriefingService.generateTodayGlobalBriefing()
  ↓
오늘 날짜 기준 프롬프트 생성 (뉴스 데이터 없음)
  ↓
GPTClientService.generateResponseAsync() → GPT API 직접 호출
  ↓
GPT가 학습 데이터 기반으로 5줄 요약 생성
  ↓
DB에 저장 (user_id = 0, status = SUCCESS)
  ↓
사용자 요청 시
  ↓
MarketBriefingService.getTodayGlobalBriefing() → DB에서만 조회
  ↓
사용자에게 반환
```

**에러 처리**:
- GPT API 타임아웃: 90초 초과 시 `status = FAILED` 저장
- GPT API 실패: `result.isSuccess() == false` 시 `status = FAILED` 저장
- 예외 발생: `catch` 블록에서 `status = FAILED` 저장
- 사용자 조회 시: 실패 상태면 기본 메시지 반환

#### 데이터베이스 스키마

```sql
-- ai_conversations 테이블 (AI 채팅 대화 기록)
CREATE TABLE ai_conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(10) NOT NULL,  -- user, assistant
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- market_briefing 테이블 (시장 브리핑)
CREATE TABLE market_briefing (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,  -- 0 = 전역 공유
    date DATE NOT NULL,
    content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',  -- SUCCESS, FAILED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, date)
);
```

---

### 5. 관심 종목 및 가격 알림

#### 구현 위치
- **Watchlist**: `watchlist/controller/WatchlistController.java`
- **PriceAlert**: `alert/controller/PriceAlertController.java`, `alert/scheduler/PriceAlertScheduler.java`

#### 주요 기능

##### 5.1 관심 종목 (Watchlist)

**엔드포인트**:
- `GET /api/watchlist`: 관심 종목 목록 조회
- `POST /api/watchlist/{stockSymbol}`: 관심 종목 추가
- `DELETE /api/watchlist/{stockSymbol}`: 관심 종목 삭제

**데이터베이스 스키마**:
```sql
CREATE TABLE watchlists (
    user_id BIGINT NOT NULL REFERENCES users(id),
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, stock_symbol)  -- 복합 기본 키
);
```

##### 5.2 가격 알림 (PriceAlert)

**엔드포인트**:
- `GET /api/alerts`: 알림 목록 조회
- `POST /api/alerts`: 알림 생성
- `DELETE /api/alerts/{alertId}`: 알림 삭제

**알림 타입**:
- `TARGET`: 목표가 도달 알림
- `STOP_LOSS`: 손절가 도달 알림
- `PERCENT`: 변동률 기준 알림

**스케줄러** (`PriceAlertScheduler`):
- **실행 주기**: 매 30초마다 실행 (`@Scheduled(fixedRate = 30000)`)
- **알림 체크 로직**:
  1. 활성화된 알림 조회 (`triggered = false`)
  2. 각 알림에 대해 현재 가격 조회 (FastAPI 또는 DB)
  3. 알림 타입별 조건 체크:
     - `TARGET`: 현재가 >= 목표가
     - `STOP_LOSS`: 현재가 <= 손절가
     - `PERCENT`: 변동률 >= 설정된 변동률
  4. 조건 충족 시:
     - `triggered = true` 설정
     - `triggered_at` 기록
     - 알림 발송 (`NotificationService.sendPriceAlert`)
- **가격 조회**:
  - FastAPI 우선 사용 (`fastApiService.getCurrentPrice()`)
  - FastAPI 실패 시 DB에서 조회 (`stock_price_history` 테이블)
- **알림 발송**:
  - 이메일 알림 (선택사항, `NOTIFICATION_EMAIL_ENABLED` 설정 필요)
  - 알림 메시지: 종목명, 목표가/손절가/변동률, 현재가 포함

**데이터베이스 스키마**:
```sql
CREATE TABLE price_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol),
    alert_type VARCHAR(20) NOT NULL,  -- TARGET, STOP_LOSS, PERCENT
    target_price NUMERIC(18,4),
    percent_change NUMERIC(5,2),
    triggered BOOLEAN DEFAULT FALSE,
    triggered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 데이터베이스 설계

### 주요 테이블 관계

```
users (1) ──< (N) portfolios ──< (N) portfolio_items >── (N) stocks
  │                                                              │
  ├──< (N) watchlists >── (N) stocks                            │
  ├──< (N) price_alerts >── (N) stocks                          │
  ├──< (N) ai_conversations                                     │
  └──< (N) email_verifications                                  │
                                                               │
stocks (1) ──< (N) stock_price_history                         │
  │                                                              │
  └──< (N) news_stock_relations >── (N) news ──< (1) news_analyses
```

### 인덱스 전략

- **기본 키**: 모든 테이블에 `id` 또는 복합 키 사용
- **외래 키**: `user_id`, `stock_symbol` 등에 인덱스 자동 생성
- **조회 최적화**: `stock_price_history`에 `(stock_symbol, timestamp)` 복합 인덱스
- **캐싱 최적화**: `portfolio_analysis`에 `user_id`, `portfolio_hash` 인덱스

---

## API 설계 원칙

### 1. RESTful API 설계

- **GET**: 조회 (포트폴리오, 주식 정보, 뉴스 등)
- **POST**: 생성 (포트폴리오 항목 추가, 알림 생성 등)
- **PUT**: 수정 (포트폴리오 항목 수정, 프로필 수정 등)
- **DELETE**: 삭제 (포트폴리오 항목 삭제, 알림 삭제 등)

### 2. 인증 및 보안

- **JWT 토큰**: 모든 API 요청에 `Authorization: Bearer {token}` 헤더 필요
- **예외**: 회원가입, 로그인 API는 인증 불필요
- **토큰 검증**: `JwtAuthenticationFilter`에서 자동 검증
- **에러 응답**: 401 Unauthorized (토큰 없음/만료), 400 Bad Request (잘못된 요청)

### 3. 에러 처리

- **일관된 에러 응답**: `ResponseEntity<?>` 사용
- **에러 메시지**: 사용자 친화적인 메시지 제공
- **로깅**: 모든 에러는 `Logger`로 기록

---

## 캐싱 전략

### 1. 포트폴리오 분석 캐싱

**목적**: 불필요한 GPT API 호출 방지로 비용 절감

**구현**:
- 포트폴리오 구성 해시 기반 캐싱
- 구성 변경 시에만 재분석
- DB에 분석 결과 저장 (`portfolio_analysis` 테이블)

**캐시 무효화 조건**:
- 종목 추가/삭제
- 수량 변경
- 평균 매입가 변경
- `forceRefresh=true` 파라미터

### 2. 시장 브리핑 캐싱

**목적**: 하루 1회만 GPT로 생성하여 비용 절감

**구현**:
- 일일 캐싱 (`market_briefing` 테이블)
- 전역 공유 (`user_id = 0`)
- 스케줄러가 장 시작 전 자동 생성

### 3. 주가 캐싱

**목적**: 외부 API 호출 최소화

**구현**:
- `stock_price_history` 테이블에 최신 가격 저장
- 시장 개장 시간에는 DB 가격만 사용
- 시장 휴장 시간에만 외부 API 호출

---

## 에러 처리 및 로깅

### 1. 로깅 전략

**Slf4j 사용**:
```java
private static final Logger log = LoggerFactory.getLogger(MyService.class);

log.info("[기능명] 작업 시작 (userId={})", userId);
log.error("[기능명] 오류 발생 (userId={}): {}", userId, e.getMessage(), e);
```

**로그 레벨**:
- `INFO`: 정상 작업 흐름
- `WARN`: 경고 (예: 중복 데이터)
- `ERROR`: 에러 (예외 발생)

### 2. 예외 처리

**컨트롤러 레벨**:
```java
try {
    // 작업 수행
    return ResponseEntity.ok(result);
} catch (IllegalArgumentException e) {
    log.warn("Invalid argument: {}", e.getMessage());
    return ResponseEntity.badRequest().body("Error: " + e.getMessage());
} catch (Exception e) {
    log.error("Exception: {} - {}", e.getClass().getName(), e.getMessage(), e);
    return ResponseEntity.status(500).body("Internal Server Error");
}
```

**서비스 레벨**:
- 비즈니스 로직 예외는 `IllegalArgumentException` 사용
- 시스템 예외는 `RuntimeException` 사용
- 예외는 상위 레이어로 전파

---

## 개발 환경 설정

### 1. Backend 설정

**필수 환경 변수**:
```bash
# JWT 시크릿 (64 bytes 이상 필수)
SKJWT_SECRET=<생성된-키>

# OpenAI API 키
OPENAI_API_KEY=sk-...

# 데이터베이스 설정 (application.properties)
spring.datasource.url=jdbc:postgresql://localhost:5432/stockknockdb
spring.datasource.username=sxxm
spring.datasource.password=sxxmpass
```

**실행 방법**:
```bash
cd knockBE
./gradlew bootRun
```

### 2. Frontend 설정

**필수 설정**:
```bash
# API Base URL (src/api/client.ts)
const API_BASE_URL = 'http://localhost:8080/api';
```

**실행 방법**:
```bash
cd knockFE
npm install
npm run dev
```

### 3. 데이터베이스 설정

**스키마 생성**:
```bash
psql -U sxxm -d stockknockdb -f knockBE/src/main/resources/db/schema.sql
```

---

## 새로운 기능 추가 가이드

### 1. 새로운 API 엔드포인트 추가

**단계**:
1. **Entity 생성** (`entity/` 폴더)
   - JPA 어노테이션 사용
   - `@Entity`, `@Table`, `@Id`, `@GeneratedValue` 등

2. **Repository 생성** (`repository/` 폴더)
   - `JpaRepository<Entity, ID>` 상속
   - 커스텀 쿼리 메서드 추가 (필요 시)

3. **Service 생성** (`service/` 폴더)
   - `@Service` 어노테이션
   - 비즈니스 로직 구현
   - 트랜잭션 관리 (`@Transactional`)

4. **Controller 생성** (`controller/` 폴더)
   - `@RestController` 어노테이션
   - `@RequestMapping("/api/...")` 경로 설정
   - JWT 인증 필터 적용 (자동)

5. **DTO 생성** (`dto/` 폴더)
   - Request DTO: API 요청 바디
   - Response DTO: API 응답 바디
   - Lombok 사용 (`@Data`, `@Builder` 등)

### 2. 새로운 AI 기능 추가

**단계**:
1. **GPTClientService 사용**:
   ```java
   AIRequestOptions options = AIRequestOptions.builder()
       .systemPrompt("시스템 프롬프트")
       .temperature(0.7)
       .maxTokens(1000)
       .timeoutSeconds(90)
       .build();
   
   AIResponseResult result = gptClientService.generateResponseAsync(prompt, options)
       .block(Duration.ofSeconds(90));
   ```

2. **캐싱 전략 고려**:
   - 자주 호출되는 기능이면 캐싱 추가
   - 포트폴리오 분석처럼 구성 변경 시에만 재분석

3. **에러 처리**:
   - GPT 호출 실패 시 기본 메시지 제공
   - 사용자에게 친화적인 에러 메시지

### 3. 새로운 스케줄러 추가

**단계**:
1. **스케줄러 클래스 생성** (`scheduler/` 폴더)
   ```java
   @Component
   public class MyScheduler {
       @Scheduled(cron = "0 0 9 * * MON-FRI")  // 평일 오전 9시
       public void myTask() {
           // 작업 수행
       }
   }
   ```

2. **스케줄러 활성화** (`StockknockApplication.java`):
   ```java
   @SpringBootApplication
   @EnableScheduling  // 이미 활성화되어 있음
   public class StockknockApplication {
       // ...
   }
   ```

---

## 참고 자료

- **Spring Boot 공식 문서**: https://spring.io/projects/spring-boot
- **JPA/Hibernate 문서**: https://hibernate.org/orm/documentation/
- **OpenAI API 문서**: https://platform.openai.com/docs
- **PostgreSQL 문서**: https://www.postgresql.org/docs/

---

**작성일**: 2024-01-XX  
**버전**: 1.0  
**작성자**: AI Assistant

