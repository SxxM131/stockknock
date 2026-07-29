import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './context/AuthContext';
import { Login } from './pages/Login';
import { Dashboard } from './pages/Dashboard';
import { Portfolio, PortfolioValueAnalysis, PortfolioProfitAnalysis } from './pages/Portfolio';
import { Watchlist } from './pages/Watchlist';
import { Alerts } from './pages/Alerts';
import { News, NewsDetail } from './pages/News';
import { AIChat } from './pages/AIChat';
import { MyPage } from './pages/MyPage';

const queryClient = new QueryClient();

const PrivateRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // 개발 편의를 위해 임시로 주석처리
  // const { isAuthenticated } = useAuth();
  // return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
  return <>{children}</>;
};

function AppRoutes() {
  // 개발 편의를 위해 임시로 주석처리
  // const { isAuthenticated } = useAuth();
  
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" />} />
      <Route path="/login" element={<Login />} />
      <Route
        path="/dashboard"
        element={
          <PrivateRoute>
            <Dashboard />
          </PrivateRoute>
        }
      />
      <Route
        path="/portfolio"
        element={
          <PrivateRoute>
            <Portfolio />
          </PrivateRoute>
        }
      />
      <Route
        path="/portfolio/analysis/value"
        element={
          <PrivateRoute>
            <PortfolioValueAnalysis />
          </PrivateRoute>
        }
      />
      <Route
        path="/portfolio/analysis/profit"
        element={
          <PrivateRoute>
            <PortfolioProfitAnalysis />
          </PrivateRoute>
        }
      />
      <Route
        path="/watchlist"
        element={
          <PrivateRoute>
            <Watchlist />
          </PrivateRoute>
        }
      />
      <Route
        path="/alerts"
        element={
          <PrivateRoute>
            <Alerts />
          </PrivateRoute>
        }
      />
      <Route
        path="/news"
        element={
          <PrivateRoute>
            <News />
          </PrivateRoute>
        }
      />
      <Route
        path="/news/:id"
        element={
          <PrivateRoute>
            <NewsDetail />
          </PrivateRoute>
        }
      />
      <Route
        path="/ai-chat"
        element={
          <PrivateRoute>
            <AIChat />
          </PrivateRoute>
        }
      />
      <Route
        path="/mypage"
        element={
          <PrivateRoute>
            <MyPage />
          </PrivateRoute>
        }
      />
    </Routes>
  );
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Router>
          <AppRoutes />
        </Router>
      </AuthProvider>
    </QueryClientProvider>
  );
}

export default App;
