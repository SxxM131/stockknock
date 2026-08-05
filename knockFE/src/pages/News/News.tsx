import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { newsAPI } from '../../api/news';
import { youtubeAPI, type YoutubeVideoDto } from '../../api/youtube';
import './News.css';

type NewsTab = 'news' | 'youtube';

const sentimentLabel = (sentiment?: string) => {
  switch ((sentiment || '').toLowerCase()) {
    case 'positive':
      return '긍정';
    case 'negative':
      return '부정';
    default:
      return '중립';
  }
};

const YoutubeBriefingCard: React.FC<{ item: YoutubeVideoDto }> = ({ item }) => {
  const summary = item.analysis?.summary || '요약이 아직 준비되지 않았습니다.';
  const stocks = item.analysis?.keyStocks || [];

  return (
    <a
      className="youtube-briefing-card"
      href={item.url}
      target="_blank"
      rel="noopener noreferrer"
    >
      {item.thumbnailUrl && (
        <img
          className="youtube-thumb"
          src={item.thumbnailUrl}
          alt={item.title}
          loading="lazy"
        />
      )}
      <div className="youtube-briefing-body">
        <div className="youtube-meta">
          <span className="youtube-channel">{item.channelName}</span>
          {item.category && <span className="youtube-category">{item.category}</span>}
          <span className="youtube-date">
            {item.publishedAt ? new Date(item.publishedAt).toLocaleDateString('ko-KR') : ''}
          </span>
        </div>
        <h3>{item.title}</h3>
        <p className="youtube-summary">{summary}</p>
        <div className="youtube-badges">
          <span className={`tone-badge ${(item.analysis?.sentiment || 'neutral').toLowerCase()}`}>
            {sentimentLabel(item.analysis?.sentiment)}
          </span>
          {item.analysis?.forecastPeriod && (
            <span className="period-badge">{item.analysis.forecastPeriod}</span>
          )}
          {stocks.map((symbol) => (
            <span key={symbol} className="stock-badge">{symbol}</span>
          ))}
        </div>
      </div>
    </a>
  );
};

const News: React.FC = () => {
  const navigate = useNavigate();
  const [tab, setTab] = useState<NewsTab>('news');
  const [selectedChannelId, setSelectedChannelId] = useState<number | undefined>(undefined);

  const { data: news, isLoading, error } = useQuery({
    queryKey: ['recentNews'],
    queryFn: () => newsAPI.getRecent(7),
    enabled: tab === 'news',
  });

  const { data: channels } = useQuery({
    queryKey: ['youtubeChannels'],
    queryFn: () => youtubeAPI.getChannels(),
    enabled: tab === 'youtube',
  });

  const {
    data: briefings,
    isLoading: briefingsLoading,
    error: briefingsError,
  } = useQuery({
    queryKey: ['youtubeBriefings', selectedChannelId],
    queryFn: () => youtubeAPI.getBriefings(selectedChannelId, 7),
    enabled: tab === 'youtube',
  });

  return (
    <div className="news">
      <h1>뉴스 · 유튜버 브리핑</h1>

      <div className="news-tabs">
        <button
          className={`news-tab ${tab === 'news' ? 'active' : ''}`}
          onClick={() => setTab('news')}
          type="button"
        >
          뉴스
        </button>
        <button
          className={`news-tab ${tab === 'youtube' ? 'active' : ''}`}
          onClick={() => setTab('youtube')}
          type="button"
        >
          유튜버 브리핑
        </button>
      </div>

      {tab === 'news' && (
        <>
          {isLoading && <div className="news-loading">로딩 중...</div>}
          {error && (
            <div className="news-error">뉴스를 불러오는 중 오류가 발생했습니다.</div>
          )}
          {!isLoading && !error && (
            <div className="news-list">
              {news && news.length > 0 ? (
                news.map((item) => (
                  <div
                    key={item.id}
                    className="news-item"
                    onClick={() => navigate(`/news/${item.id}`)}
                  >
                    <h3>{item.title}</h3>
                    <p className="source">
                      {item.source} · {new Date(item.publishedAt || '').toLocaleDateString()}
                    </p>
                    <p className="content">{(item.content || '').substring(0, 200)}...</p>
                    {item.analysis && (
                      <div className="analysis">
                        <h4>AI 분석</h4>
                        <p>{item.analysis.summary}</p>
                        <p className={`sentiment ${item.analysis.sentiment?.toLowerCase() || ''}`}>
                          감정: {item.analysis.sentiment} (영향도: {item.analysis.impactScore}/10)
                        </p>
                      </div>
                    )}
                    <button className="btn-read-more" type="button">
                      자세히 보기 →
                    </button>
                  </div>
                ))
              ) : (
                <div className="news-empty">표시할 뉴스가 없습니다.</div>
              )}
            </div>
          )}
        </>
      )}

      {tab === 'youtube' && (
        <>
          <div className="youtube-filters">
            <button
              type="button"
              className={`channel-chip ${selectedChannelId == null ? 'active' : ''}`}
              onClick={() => setSelectedChannelId(undefined)}
            >
              전체
            </button>
            {channels?.map((ch) => (
              <button
                key={ch.id}
                type="button"
                className={`channel-chip ${selectedChannelId === ch.id ? 'active' : ''}`}
                onClick={() => setSelectedChannelId(ch.id)}
              >
                {ch.channelName}
              </button>
            ))}
          </div>

          {briefingsLoading && <div className="news-loading">브리핑 로딩 중...</div>}
          {briefingsError && (
            <div className="news-error">유튜버 브리핑을 불러오는 중 오류가 발생했습니다.</div>
          )}
          {!briefingsLoading && !briefingsError && (
            <div className="youtube-briefing-list">
              {briefings && briefings.length > 0 ? (
                briefings.map((item) => <YoutubeBriefingCard key={item.id} item={item} />)
              ) : (
                <div className="news-empty">
                  표시할 유튜버 브리핑이 없습니다.
                  <br />
                  채널 ID를 시드에 채운 뒤 스케줄러(08:30 / 15:30) 또는 YouTube API 키를 확인하세요.
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default News;
