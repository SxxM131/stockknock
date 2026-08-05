package com.sxxm.stockknock.youtube.entity;

/**
 * 유튜브 영상 AI 분석 상태
 * PENDING  — 신규 수집, 미분석
 * SUCCESS  — 분석 완료 (재분석 금지)
 * FAILED   — 분석 실패, 다음 스케줄에서 재시도
 * SKIPPED  — 자막/설명 없음으로 스킵
 */
public enum VideoAnalysisStatus {
    PENDING,
    SUCCESS,
    FAILED,
    SKIPPED
}
