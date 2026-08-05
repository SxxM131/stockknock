-- ============================================
-- StocKKnock Database Schema
-- PostgreSQL 12+ / Supabase 호환
-- ============================================
--
-- 이 스키마는 StocKKnock 애플리케이션의 데이터베이스 구조를 정의합니다.
-- 총 14개의 테이블로 구성되어 있습니다.
--
-- ── 로컬 psql 실행 ────────────────────────────────────────────────────────
--   psql -U your_db_user -d stockknockdb -h localhost -f schema.sql
--   PGPASSWORD="$DB_PASSWORD" psql -U "$DB_USERNAME" -d stockknockdb -h localhost -f schema.sql
--
-- ── Supabase SQL Editor 실행 ──────────────────────────────────────────────
--   1. Supabase 대시보드 → SQL Editor → New Query
--   2. 이 파일 전체를 붙여넣고 Run (F5)
--   3. 이미 테이블/인덱스가 있어도 IF NOT EXISTS 이므로 재실행 안전
--
-- ── Supabase 호환 참고사항 ────────────────────────────────────────────────
--   - BIGSERIAL (= BIGINT + sequence) : Supabase 완전 지원
--   - NUMERIC, TEXT, BOOLEAN, TIMESTAMP, DATE : 모두 지원
--   - ON DELETE CASCADE 외래키 : 지원
--   - PARTIAL INDEX (WHERE 절 포함) : PostgreSQL 12+ 지원 ← Supabase 해당
--   - Supabase 는 기본적으로 public 스키마에 생성됩니다.
--   - RLS(Row Level Security)는 이 스크립트에서 설정하지 않습니다.
--     필요 시 Supabase 대시보드 > Authentication > Policies 에서 추가하세요.
-- ============================================

-- ============================================
-- 1. users (사용자)
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 참고: UNIQUE 제약조건이 자동으로 인덱스를 생성하므로 별도 인덱스는 선택사항
-- 성능 최적화를 위해 명시적으로 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- ============================================
-- 2. stocks (종목 기본 정보)
-- ============================================
CREATE TABLE IF NOT EXISTS stocks (
    symbol VARCHAR(20) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    exchange VARCHAR(50),
    country VARCHAR(50),
    industry VARCHAR(100),
    currency VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stocks_exchange ON stocks(exchange);
CREATE INDEX IF NOT EXISTS idx_stocks_country ON stocks(country);
CREATE INDEX IF NOT EXISTS idx_stocks_industry ON stocks(industry);

-- ============================================
-- 3. stock_price_history (종목 시세 히스토리)
-- ============================================
CREATE TABLE IF NOT EXISTS stock_price_history (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol) ON DELETE CASCADE,
    price NUMERIC(18,4) NOT NULL,
    open NUMERIC(18,4),
    high NUMERIC(18,4),
    low NUMERIC(18,4),
    volume BIGINT,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(stock_symbol, timestamp)
);

CREATE INDEX IF NOT EXISTS idx_price_history_stock_time ON stock_price_history(stock_symbol, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_price_history_timestamp ON stock_price_history(timestamp DESC);

-- ============================================
-- 4. portfolio (사용자 포트폴리오)
-- ============================================
CREATE TABLE IF NOT EXISTS portfolio (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) DEFAULT 'Default Portfolio',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_portfolio_user ON portfolio(user_id);

-- ============================================
-- 5. portfolio_item (포트폴리오 개별 보유 종목)
-- ============================================
CREATE TABLE IF NOT EXISTS portfolio_item (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE,
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol) ON DELETE CASCADE,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0,
    avg_buy_price NUMERIC(18,4) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_portfolio_stock ON portfolio_item(portfolio_id, stock_symbol);
CREATE INDEX IF NOT EXISTS idx_portfolio_item_stock ON portfolio_item(stock_symbol);

-- ============================================
-- 5-1. portfolio_analysis (포트폴리오 AI 분석 결과)
-- ============================================
CREATE TABLE IF NOT EXISTS portfolio_analysis (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    portfolio_hash VARCHAR(64) NOT NULL,
    analysis_content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_portfolio_analysis_user ON portfolio_analysis(user_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_analysis_hash ON portfolio_analysis(portfolio_hash);

-- ============================================
-- 6. watchlist (관심 종목)
-- ============================================
CREATE TABLE IF NOT EXISTS watchlist (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, stock_symbol)
);

CREATE INDEX IF NOT EXISTS idx_watchlist_user ON watchlist(user_id);
CREATE INDEX IF NOT EXISTS idx_watchlist_stock ON watchlist(stock_symbol);

-- ============================================
-- 7. price_alert (가격 알림 설정)
-- ============================================
CREATE TABLE IF NOT EXISTS price_alert (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol) ON DELETE CASCADE,
    alert_type VARCHAR(20) NOT NULL, -- 'TARGET', 'STOP_LOSS', 'PERCENT'
    target_price NUMERIC(18,4),
    percent_change NUMERIC(5,2),
    triggered BOOLEAN DEFAULT FALSE,
    triggered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_price_alert_user ON price_alert(user_id);
CREATE INDEX IF NOT EXISTS idx_price_alert_stock ON price_alert(stock_symbol);
CREATE INDEX IF NOT EXISTS idx_price_alert_active ON price_alert(user_id, triggered) WHERE triggered = FALSE;

-- ============================================
-- 8. news (수집된 뉴스 원문)
-- ============================================
CREATE TABLE IF NOT EXISTS news (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT,
    url TEXT,
    source TEXT,
    published_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_news_published ON news(published_at DESC);
CREATE INDEX IF NOT EXISTS idx_news_created ON news(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_news_source ON news(source);

-- ============================================
-- 9. news_analysis (AI 분석 데이터)
-- ============================================
-- 주의: news_id가 PRIMARY KEY이므로 각 뉴스당 하나의 분석만 존재할 수 있습니다.
-- @MapsId를 사용하지 않고 일반적인 OneToOne 관계를 사용합니다.
CREATE TABLE IF NOT EXISTS news_analysis (
    news_id BIGINT PRIMARY KEY REFERENCES news(id) ON DELETE CASCADE,
    summary TEXT,
    sentiment VARCHAR(20), -- 'positive', 'negative', 'neutral'
    impact_score INT CHECK (impact_score >= 1 AND impact_score <= 10),
    ai_comment TEXT,
    analyzed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_news_analysis_sentiment ON news_analysis(sentiment);
CREATE INDEX IF NOT EXISTS idx_news_analysis_impact ON news_analysis(impact_score DESC);

-- ============================================
-- 10. news_stock_relation (뉴스 ↔ 종목 연관 N:M)
-- ============================================
CREATE TABLE IF NOT EXISTS news_stock_relation (
    news_id BIGINT NOT NULL REFERENCES news(id) ON DELETE CASCADE,
    stock_symbol VARCHAR(20) NOT NULL REFERENCES stocks(symbol) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (news_id, stock_symbol)
);

CREATE INDEX IF NOT EXISTS idx_news_stock_news ON news_stock_relation(news_id);
CREATE INDEX IF NOT EXISTS idx_news_stock_stock ON news_stock_relation(stock_symbol);

-- ============================================
-- 11. ai_conversation (AI 대화 기록)
-- ============================================
CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL, -- 'user', 'assistant'
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_user_time ON ai_conversation(user_id, created_at DESC);

-- ============================================
-- 13. market_briefing (시장 브리핑 캐시)
-- ============================================
CREATE TABLE IF NOT EXISTS market_briefing (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, date)
);

CREATE INDEX IF NOT EXISTS idx_market_briefing_user_date ON market_briefing(user_id, date DESC);

-- ============================================
-- 14. youtube_channels (유튜버 화이트리스트)
-- ============================================
CREATE TABLE IF NOT EXISTS youtube_channels (
    id BIGSERIAL PRIMARY KEY,
    channel_id VARCHAR(64) NOT NULL UNIQUE, -- YouTube 채널 고유 ID (UCxxxx). PLACEHOLDER_ 는 나중에 교체
    channel_name VARCHAR(200) NOT NULL,
    category VARCHAR(50), -- 국내주식 / 미국주식 / 거시경제
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_youtube_channels_active ON youtube_channels(is_active);

-- Phase 1 시드 (channel_id 는 플레이스홀더 — 실제 UC... ID로 교체 필요)
INSERT INTO youtube_channels (channel_id, channel_name, category, is_active)
VALUES
    ('PLACEHOLDER_UC_SAMPROTV', '삼프로TV', '국내주식', TRUE),
    ('PLACEHOLDER_UC_SHINSAIMDANG', '신사임당', '국내주식', TRUE),
    ('PLACEHOLDER_UC_SYUKAWORLD', '슈카월드', '거시경제', TRUE),
    ('PLACEHOLDER_UC_BOOKKUMI', '부꾸미', '국내주식', TRUE),
    ('PLACEHOLDER_UC_STOCKREADER', '주식읽는남자', '국내주식', TRUE),
    ('PLACEHOLDER_UC_SOSUMONKEY', '소수몽키', '미국주식', TRUE),
    ('PLACEHOLDER_UC_INVESTGOD', '투자의 신', '거시경제', TRUE),
    ('PLACEHOLDER_UC_PARKPROF', '박교수', '미국주식', TRUE)
ON CONFLICT (channel_id) DO NOTHING;

-- ============================================
-- 15. youtube_videos (수집된 영상 메타 — 자막 원문 저장 금지)
-- ============================================
CREATE TABLE IF NOT EXISTS youtube_videos (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES youtube_channels(id) ON DELETE CASCADE,
    video_id VARCHAR(32) NOT NULL UNIQUE, -- YouTube 영상 ID
    title TEXT,
    url TEXT,
    published_at TIMESTAMP,
    thumbnail_url TEXT,
    analysis_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING/SUCCESS/FAILED/SKIPPED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_youtube_videos_published ON youtube_videos(published_at DESC);
CREATE INDEX IF NOT EXISTS idx_youtube_videos_channel ON youtube_videos(channel_id);
CREATE INDEX IF NOT EXISTS idx_youtube_videos_status ON youtube_videos(analysis_status);

-- ============================================
-- 16. youtube_video_analyses (AI 요약만 저장, video_id = PK)
-- ============================================
CREATE TABLE IF NOT EXISTS youtube_video_analyses (
    video_id BIGINT PRIMARY KEY REFERENCES youtube_videos(id) ON DELETE CASCADE,
    summary TEXT,
    key_stocks TEXT, -- JSON 배열 문자열 예: ["005930","AAPL"]
    forecast_period VARCHAR(20), -- 단기/중기/장기
    sentiment VARCHAR(20), -- positive/negative/neutral
    ai_comment TEXT,
    analyzed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_youtube_analyses_sentiment ON youtube_video_analyses(sentiment);

-- ============================================
-- 12. email_verification (이메일 인증 코드)
-- ============================================
CREATE TABLE IF NOT EXISTS email_verification (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    verification_code VARCHAR(6) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_email_verification_email ON email_verification(email);
CREATE INDEX IF NOT EXISTS idx_email_verification_code ON email_verification(verification_code);

-- ============================================
-- 스키마 생성 완료
-- ============================================
-- 총 테이블:
-- 1. users
-- 2. stocks
-- 3. stock_price_history
-- 4. portfolio
-- 5. portfolio_item
-- 5-1. portfolio_analysis
-- 6. watchlist
-- 7. price_alert
-- 8. news
-- 9. news_analysis
-- 10. news_stock_relation
-- 11. ai_conversation
-- 12. email_verification
-- 13. market_briefing
-- 14. youtube_channels
-- 15. youtube_videos
-- 16. youtube_video_analyses
-- ============================================
