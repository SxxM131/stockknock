"""
database.py — SQLAlchemy 엔진 설정

환경 변수 로드 우선순위:
  1. .env.supabase  (SUPABASE=1 이 설정돼 있거나 해당 파일이 존재하면 로드)
  2. .env           (기본 로컬/공통 설정)

DATABASE_URL 형식:
  - 로컬:    postgresql://user:password@localhost:5432/stockknockdb
  - Supabase: postgresql://postgres.[ref]:[password]@[host]:5432/postgres?sslmode=require

SSL:
  - URL 에 ?sslmode=require 가 포함되면 SQLAlchemy 가 자동 처리합니다.
  - DB_SSLMODE=require 환경 변수를 따로 설정하면 URL 에 sslmode 쿼리가 없어도
    connect_args 로 주입합니다.
"""

import os
from pathlib import Path
from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from dotenv import load_dotenv

_base_dir = Path(__file__).resolve().parent.parent  # knockAI/

# Supabase 프로필: SUPABASE=1 이거나 .env.supabase 파일이 있을 때 우선 로드
_supabase_env = _base_dir / ".env.supabase"
if os.getenv("SUPABASE") == "1" or _supabase_env.exists():
    load_dotenv(_supabase_env, override=True)

# 공통 .env (없어도 오류 없음)
load_dotenv(_base_dir / ".env", override=False)

# ── DATABASE_URL 필수 검증 ──────────────────────────────────────────────────
DATABASE_URL = os.getenv("DATABASE_URL")
if not DATABASE_URL:
    raise RuntimeError(
        "DATABASE_URL 환경 변수가 설정되지 않았습니다.\n"
        "  로컬: knockAI/.env 에 DATABASE_URL=postgresql://user:pass@localhost:5432/db\n"
        "  Supabase: knockAI/.env.supabase 에 DATABASE_URL=postgresql://...?sslmode=require"
    )

# ── SSL connect_args 구성 ──────────────────────────────────────────────────
# URL 에 ?sslmode=require 가 이미 있으면 SQLAlchemy 가 알아서 처리.
# DB_SSLMODE=require 환경 변수로 별도 강제도 가능.
_connect_args: dict = {}
_sslmode = os.getenv("DB_SSLMODE", "")
if _sslmode and "sslmode" not in DATABASE_URL:
    _connect_args["sslmode"] = _sslmode

# ── 엔진 생성 ─────────────────────────────────────────────────────────────
engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
    connect_args=_connect_args if _connect_args else {},
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def get_db():
    """데이터베이스 세션 의존성 (FastAPI Depends 용)"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
