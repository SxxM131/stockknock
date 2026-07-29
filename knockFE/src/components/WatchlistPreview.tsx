import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { StockDto } from '../api/stock';
import './WatchlistPreview.css';

interface WatchlistPreviewProps {
  watchlist: StockDto[];
}

// 종목 코드 -> 종목명 매핑 (DB에 종목명이 없을 때 사용)
const stockNameMap: Record<string, string> = {
  '005930': '삼성전자',
  '000660': 'SK하이닉스',
  '035420': 'NAVER',
  '035720': '카카오',
  '005380': '현대자동차',
  '066570': 'LG전자',
  '068270': '셀트리온',
  '005490': 'POSCO홀딩스',
  '051910': 'LG화학',
  '006400': '삼성SDI',
  '028260': '삼성물산',
  '207940': '삼성바이오로직스',
  '000990': 'DB하이텍',
  '012330': '현대모비스',
  '373220': 'LG에너지솔루션',
  '096770': 'SK이노베이션',
  '105560': 'KB금융',
  '055550': '신한지주',
  '086790': '하나금융지주',
  '316140': '우리금융지주',
  '000270': '기아',
  '003670': '포스코홀딩스',
};

// 종목명 가져오기
const getStockName = (stock: StockDto) => {
  if (stock.name && stock.name !== stock.symbol) {
    return stock.name;
  }
  return stockNameMap[stock.symbol] || stock.symbol;
};

// 등락률 포맷팅 (대시보드용 - 등락률 위주)
const formatChange = (current: number | undefined, previous: number | undefined) => {
  if (!current || !previous) {
    return { value: '0.00%', isPositive: true };
  }
  const change = current - previous;
  const percent = ((change / previous) * 100).toFixed(2);
  return {
    value: `${change >= 0 ? '+' : ''}${percent}%`,
    isPositive: change >= 0,
  };
};

// 업데이트 시점 포맷팅
const formatLastUpdated = (lastUpdated?: string) => {
  if (!lastUpdated) return '업데이트 정보 없음';
  
  try {
    const updated = new Date(lastUpdated);
    const now = new Date();
    const diffMs = now.getTime() - updated.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    
    if (diffMins < 1) return '방금 전';
    if (diffMins < 60) return `${diffMins}분 전`;
    
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}시간 전`;
    
    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays}일 전`;
  } catch {
    return '업데이트 정보 없음';
  }
};

const WatchlistPreview: React.FC<WatchlistPreviewProps> = ({ watchlist }) => {
  const navigate = useNavigate();

  if (!watchlist || watchlist.length === 0) {
    return null;
  }

  return (
    <section className="watchlist-preview">
      <div className="section-header">
        <h2 className="section-title">
          관심 종목
          <button className="section-link" onClick={() => navigate('/watchlist')}>
            →
          </button>
        </h2>
      </div>
      <div className="watchlist-preview-grid">
        {watchlist.slice(0, 5).map((stock) => {
          const stockName = getStockName(stock);
          const change = formatChange(stock.currentPrice, stock.previousClose);

          return (
            <div key={stock.symbol} className="watchlist-preview-card">
              <div className="watchlist-card-header">
                <div className="watchlist-stock-name">{stockName}</div>
                <div className="watchlist-stock-symbol">{stock.symbol}</div>
              </div>
              <div className="watchlist-card-body">
                {/* 대시보드: 등락률 위주로 표시 */}
                <div className={`watchlist-change-main ${change.isPositive ? 'positive' : 'negative'}`}>
                  {change.isPositive ? '▲' : '▼'} {change.value}
                </div>
                <div className="watchlist-update-info">
                  최근 업데이트: {formatLastUpdated(stock.lastUpdated)}
                </div>
                <div className="watchlist-update-hint">
                  약 10분 주기로 갱신
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
};

export default WatchlistPreview;

