import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { newsAPI } from '../../api/news';
import './NewsDetail.css';

const NewsDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const newsId = id ? parseInt(id, 10) : 0;

  const { data: news, isLoading } = useQuery({
    queryKey: ['news', newsId],
    queryFn: () => newsAPI.getById(newsId),
    enabled: !!newsId,
  });

  const analyzeMutation = useMutation({
    mutationFn: () => newsAPI.analyze(newsId),
    onSuccess: () => {
      // 분석 완료 후 데이터 다시 불러오기
      window.location.reload();
    },
  });

  const getSentimentColor = (sentiment: string) => {
    switch (sentiment?.toUpperCase()) {
      case 'POSITIVE':
        return '#16a34a';
      case 'NEGATIVE':
        return '#111827';
      case 'NEUTRAL':
        return '#666';
      default:
        return '#666';
    }
  };

  const getSentimentLabel = (sentiment: string) => {
    switch (sentiment?.toUpperCase()) {
      case 'POSITIVE':
        return '긍정적';
      case 'NEGATIVE':
        return '부정적';
      case 'NEUTRAL':
        return '중립';
      default:
        return sentiment || '분석 없음';
    }
  };

  if (isLoading) {
    return (
      <div className="news-detail-container">
        <div className="loading">로딩 중...</div>
      </div>
    );
  }

  if (!news) {
    return (
      <div className="news-detail-container">
        <div className="error">뉴스를 찾을 수 없습니다.</div>
      </div>
    );
  }

  return (
    <div className="news-detail-container">
      <button className="btn-back" onClick={() => navigate('/news')}>
        ← 목록으로
      </button>

      <article className="news-article">
        <header className="news-header">
          <h1>{news.title}</h1>
          <div className="news-meta">
            <span className="source">{news.source}</span>
            <span className="date">
              {new Date(news.publishedAt).toLocaleString('ko-KR')}
            </span>
          </div>
          {news.relatedStockSymbols && news.relatedStockSymbols.length > 0 && (
            <div className="related-stocks">
              <span className="label">관련 종목:</span>
              {news.relatedStockSymbols.map((symbol) => (
                <span key={symbol} className="stock-tag">
                  {symbol}
                </span>
              ))}
            </div>
          )}
        </header>

        <div className="news-content">
          <p>{news.content}</p>
          {news.url && (
            <a href={news.url} target="_blank" rel="noopener noreferrer" className="original-link">
              원문 보기 →
            </a>
          )}
        </div>

        <div className="news-analysis-section">
          <div className="analysis-header">
            <h2>AI 분석</h2>
            {!news.analysis && (
              <button
                className="btn-analyze"
                onClick={() => analyzeMutation.mutate()}
                disabled={analyzeMutation.isPending}
              >
                {analyzeMutation.isPending ? '분석 중...' : '분석 요청'}
              </button>
            )}
          </div>

          {news.analysis ? (
            <div className="analysis-content">
              <div className="analysis-summary">
                <h3>요약</h3>
                <p>{news.analysis.summary}</p>
              </div>

              <div className="analysis-impact">
                <h3>영향 분석</h3>
                <p>{news.analysis.impactAnalysis}</p>
              </div>

              <div className="analysis-metrics">
                <div className="metric-item">
                  <span className="metric-label">감정 분석</span>
                  <span
                    className="metric-value sentiment"
                    style={{ color: getSentimentColor(news.analysis.sentiment) }}
                  >
                    {getSentimentLabel(news.analysis.sentiment)}
                  </span>
                </div>
                <div className="metric-item">
                  <span className="metric-label">영향 점수</span>
                  <span className="metric-value">{news.analysis.impactScore}/10</span>
                </div>
              </div>
            </div>
          ) : (
            <div className="no-analysis">
              <p>아직 분석되지 않은 뉴스입니다. 분석을 요청해주세요.</p>
            </div>
          )}
        </div>
      </article>
    </div>
  );
};

export default NewsDetail;

