import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { portfolioAPI, type PortfolioDto, type PortfolioAnalysisDto } from '../../api/portfolio';
import { parseMarkdown } from '../../utils/markdownParser';
import './PortfolioProfitAnalysis.css';

const PortfolioProfitAnalysis: React.FC = () => {
  const { data: portfolio, isLoading } = useQuery<PortfolioDto[]>({
    queryKey: ['portfolio'],
    queryFn: () => portfolioAPI.getAll(),
  });

  const { data: analysis, isLoading: analysisLoading } = useQuery<PortfolioAnalysisDto>({
    queryKey: ['portfolioAnalysis'],
    queryFn: () => portfolioAPI.getAnalysis(),
  });

  const totalProfitLoss = portfolio?.reduce((sum, item) => sum + item.profitLoss, 0) || 0;
  const totalProfitLossRate = analysis?.totalProfitLossRate || 0;
  const isProfit = totalProfitLoss >= 0;

  if (isLoading || analysisLoading) {
    return (
      <div className="portfolio-analysis-page">
        <div className="loading">로딩 중...</div>
      </div>
    );
  }

  // 손익이 큰 순서로 정렬
  const sortedPortfolio = portfolio
    ? [...portfolio].sort((a, b) => Math.abs(b.profitLoss) - Math.abs(a.profitLoss))
    : [];

  return (
    <div className="portfolio-analysis-page">
      <div className="analysis-header">
        <h1>오늘 손익 분석</h1>
        <div className={`profit-display ${isProfit ? 'profit' : 'loss'}`}>
          {isProfit ? '+' : ''}{totalProfitLoss.toLocaleString()}원
          <span className="profit-rate">
            ({totalProfitLossRate >= 0 ? '+' : ''}{totalProfitLossRate.toFixed(2)}%)
          </span>
        </div>
      </div>

      <div className="analysis-content">
        <div className="analysis-card">
          <h2>종목별 손익</h2>
          <div className="profit-breakdown">
            {sortedPortfolio.length > 0 ? (
              sortedPortfolio.map((item) => {
                const isItemProfit = item.profitLoss >= 0;
                return (
                  <div key={item.id} className="profit-item">
                    <div className="profit-header">
                      <div>
                        <h3>{item.stock.name}</h3>
                        <p className="stock-symbol">{item.stock.symbol}</p>
                      </div>
                      <div className={`profit-amount ${isItemProfit ? 'profit' : 'loss'}`}>
                        {isItemProfit ? '+' : ''}{item.profitLoss.toLocaleString()}원
                        <span className="profit-percentage">
                          ({item.profitLossRate >= 0 ? '+' : ''}{item.profitLossRate.toFixed(2)}%)
                        </span>
                      </div>
                    </div>
                    <div className="profit-details">
                      <div className="detail-row">
                        <span className="detail-label">보유량</span>
                        <span className="detail-value">{item.quantity}주</span>
                      </div>
                      <div className="detail-row">
                        <span className="detail-label">평균 매입가</span>
                        <span className="detail-value">{item.avgBuyPrice.toLocaleString()}원</span>
                      </div>
                      <div className="detail-row">
                        <span className="detail-label">현재가</span>
                        <span className="detail-value">{item.currentPrice.toLocaleString()}원</span>
                      </div>
                      <div className="detail-row">
                        <span className="detail-label">평가액</span>
                        <span className="detail-value">{item.totalValue.toLocaleString()}원</span>
                      </div>
                    </div>
                  </div>
                );
              })
            ) : (
              <div className="empty-state">포트폴리오가 비어있습니다.</div>
            )}
          </div>
        </div>

        {analysis && (
          <div className="analysis-card">
            <h2>손익 요약</h2>
            <div className="profit-summary">
              <div className="summary-row">
                <span className="summary-label">총 손익</span>
                <span className={`summary-value ${isProfit ? 'profit' : 'loss'}`}>
                  {isProfit ? '+' : ''}{analysis.totalProfitLoss.toLocaleString()}원
                </span>
              </div>
              <div className="summary-row">
                <span className="summary-label">수익률</span>
                <span className={`summary-value ${totalProfitLossRate >= 0 ? 'profit' : 'loss'}`}>
                  {totalProfitLossRate >= 0 ? '+' : ''}{totalProfitLossRate.toFixed(2)}%
                </span>
              </div>
              <div className="summary-row">
                <span className="summary-label">총 평가액</span>
                <span className="summary-value">{analysis.totalValue.toLocaleString()}원</span>
              </div>
            </div>
          </div>
        )}

        {analysis && (
          <div className="analysis-card">
            <h2>AI 분석</h2>
            <div className="ai-analysis-content">
              <div 
                className="analysis-text" 
                dangerouslySetInnerHTML={{ __html: parseMarkdown(analysis.analysis || '') }}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default PortfolioProfitAnalysis;

