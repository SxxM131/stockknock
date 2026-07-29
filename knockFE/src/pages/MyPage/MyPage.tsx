import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { authAPI, type UserUpdateRequest } from '../../api/auth';
import type { UserProfile } from '../../api/auth';
import { useAuth } from '../../context/AuthContext';
import './MyPage.css';

// 타입 인라인 정의 (Vite 캐시 문제 해결)
interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}

const MyPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<'profile' | 'password'>('profile');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // 프로필 수정 폼 상태
  const [username, setUsername] = useState('');
  const [nickname, setNickname] = useState('');

  // 비밀번호 변경 폼 상태
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // 사용자 정보 조회
  const { data: profile, isLoading, error: profileError } = useQuery<UserProfile>({
    queryKey: ['userProfile'],
    queryFn: () => authAPI.getProfile(),
    enabled: !!user && !!localStorage.getItem('token'),
    retry: false,
  });

  // TanStack Query v5 — onError 콜백 대신 useEffect 로 에러 처리
  useEffect(() => {
    if (!profileError) return;
    const err = profileError as any;
    if (err.response?.status === 400 || err.response?.status === 401) {
      console.warn('토큰이 유효하지 않습니다. 로그아웃 처리합니다.');
      logout();
      navigate('/login');
    }
  }, [profileError, logout, navigate]);

  // 프로필 정보 초기화
  useEffect(() => {
    if (profile) {
      setUsername(profile.username);
      setNickname(profile.nickname || '');
    }
  }, [profile]);

  // 프로필 수정
  const updateProfileMutation = useMutation({
    mutationFn: (data: UserUpdateRequest) => authAPI.updateProfile(data),
    onSuccess: () => {
      setSuccess('프로필이 수정되었습니다.');
      setError('');
      queryClient.invalidateQueries({ queryKey: ['userProfile'] });
      setTimeout(() => setSuccess(''), 3000);
    },
    onError: (err: any) => {
      setError(err.response?.data?.error || '프로필 수정에 실패했습니다.');
      setSuccess('');
    },
  });

  // 비밀번호 변경
  const changePasswordMutation = useMutation({
    mutationFn: (data: PasswordChangeRequest) => authAPI.changePassword(data),
    onSuccess: () => {
      setSuccess('비밀번호가 변경되었습니다.');
      setError('');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setTimeout(() => setSuccess(''), 3000);
    },
    onError: (err: any) => {
      setError(err.response?.data?.error || '비밀번호 변경에 실패했습니다.');
      setSuccess('');
    },
  });

  // 아이디 변경
  const changeUsernameMutation = useMutation({
    mutationFn: ({ newUsername, password }: { newUsername: string; password: string }) =>
      authAPI.changeUsername(newUsername, password),
    onSuccess: () => {
      setSuccess('아이디가 변경되었습니다. 다시 로그인해주세요.');
      setError('');
      setTimeout(() => {
        logout();
        navigate('/login');
      }, 2000);
    },
    onError: (err: any) => {
      setError(err.response?.data?.error || '아이디 변경에 실패했습니다.');
      setSuccess('');
    },
  });

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    const updates: UserUpdateRequest = {};
    let hasChanges = false;

    if (nickname !== (profile?.nickname || '')) {
      updates.nickname = nickname;
      hasChanges = true;
    }

    if (!hasChanges) {
      setError('변경할 내용이 없습니다.');
      return;
    }

    updateProfileMutation.mutate(updates);
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!currentPassword || !newPassword || !confirmPassword) {
      setError('모든 필드를 입력해주세요.');
      return;
    }

    if (newPassword.length < 6) {
      setError('비밀번호는 최소 6자 이상이어야 합니다.');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    if (currentPassword === newPassword) {
      setError('현재 비밀번호와 새 비밀번호가 동일합니다.');
      return;
    }

    changePasswordMutation.mutate({
      currentPassword,
      newPassword,
    });
  };

  const handleChangeUsername = async () => {
    setError('');
    setSuccess('');

    if (!username || username.trim() === '') {
      setError('새 아이디를 입력해주세요.');
      return;
    }

    if (username === profile?.username) {
      setError('현재 아이디와 동일합니다.');
      return;
    }

    const password = prompt('비밀번호를 입력해주세요:');
    if (!password) {
      return;
    }

    changeUsernameMutation.mutate({ newUsername: username.trim(), password });
  };

  if (isLoading) {
    return (
      <div className="mypage-container">
        <div className="mypage-box">
          <div className="loading">로딩 중...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="mypage-container">
      <div className="mypage-box">
        <div className="mypage-header">
          <h1>마이페이지</h1>
          <p className="subtitle">개인정보를 관리하고 수정할 수 있습니다</p>
        </div>

        {error && (
          <div className="alert alert-error">
            <span className="alert-icon">⚠️</span>
            <span>{error}</span>
          </div>
        )}
        {success && (
          <div className="alert alert-success">
            <span className="alert-icon">✓</span>
            <span>{success}</span>
          </div>
        )}

        <div className="tab-container">
          <button
            className={`tab-button ${activeTab === 'profile' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('profile');
              setError('');
              setSuccess('');
            }}
          >
            프로필 수정
          </button>
          <button
            className={`tab-button ${activeTab === 'password' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('password');
              setError('');
              setSuccess('');
            }}
          >
            비밀번호 변경
          </button>
        </div>

        <div className="content-area">
          {activeTab === 'profile' && (
            <div className="profile-tab">
              {/* 현재 정보 카드 */}
              <div className="info-card">
                <h3 className="card-title">현재 정보</h3>
                <div className="info-grid">
                  <div className="info-row">
                    <span className="info-label">아이디</span>
                    <span className="info-value">{profile?.username}</span>
                  </div>
                  <div className="info-row">
                    <span className="info-label">이메일</span>
                    <span className="info-value">{profile?.email}</span>
                  </div>
                  <div className="info-row">
                    <span className="info-label">닉네임</span>
                    <span className="info-value">{profile?.nickname || '설정되지 않음'}</span>
                  </div>
                </div>
              </div>

              {/* 아이디 변경 카드 */}
              <div className="form-card">
                <h3 className="card-title">아이디 변경</h3>
                <div className="form-group">
                  <label htmlFor="username">새 아이디</label>
                  <div className="username-change-group">
                    <input
                      id="username"
                      type="text"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="변경할 아이디를 입력하세요"
                      disabled={changeUsernameMutation.isPending}
                    />
                    <button
                      type="button"
                      className="btn-primary btn-change"
                      onClick={handleChangeUsername}
                      disabled={changeUsernameMutation.isPending || !username || username === profile?.username}
                    >
                      {changeUsernameMutation.isPending ? '변경 중...' : '변경'}
                    </button>
                  </div>
                </div>
              </div>

              {/* 닉네임 수정 카드 */}
              <div className="form-card">
                <h3 className="card-title">닉네임 수정</h3>
                <div className="form-group">
                  <label htmlFor="nickname">닉네임</label>
                  <div className="username-change-group">
                    <input
                      id="nickname"
                      type="text"
                      value={nickname}
                      onChange={(e) => setNickname(e.target.value)}
                      placeholder="닉네임을 입력하세요"
                      disabled={updateProfileMutation.isPending}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          handleUpdateProfile(e as any);
                        }
                      }}
                    />
                    <button
                      type="button"
                      className="btn-primary btn-change"
                      onClick={handleUpdateProfile}
                      disabled={updateProfileMutation.isPending || !nickname || nickname === (profile?.nickname || '')}
                    >
                      {updateProfileMutation.isPending ? '저장 중...' : '저장'}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'password' && (
            <div className="password-tab">
              {/* 비밀번호 변경 안내 카드 */}
              <div className="info-card">
                <h3 className="card-title">비밀번호 변경 안내</h3>
                <div className="info-grid">
                  <div className="info-row">
                    <span className="info-label">비밀번호 규칙</span>
                    <span className="info-value">최소 6자 이상</span>
                  </div>
                  <div className="info-row">
                    <span className="info-label">보안 권장사항</span>
                    <span className="info-value">영문, 숫자, 특수문자 조합 권장</span>
                  </div>
                </div>
              </div>

              {/* 비밀번호 변경 카드 */}
              <div className="form-card">
                <h3 className="card-title">비밀번호 변경</h3>
                <form onSubmit={handleChangePassword}>
                  <div className="form-group">
                    <label htmlFor="currentPassword">현재 비밀번호</label>
                    <input
                      id="currentPassword"
                      type="password"
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                      placeholder="현재 비밀번호를 입력하세요"
                      disabled={changePasswordMutation.isPending}
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="newPassword">새 비밀번호</label>
                    <input
                      id="newPassword"
                      type="password"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="새 비밀번호를 입력하세요 (최소 6자)"
                      disabled={changePasswordMutation.isPending}
                      required
                      minLength={6}
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="confirmPassword">새 비밀번호 확인</label>
                    <input
                      id="confirmPassword"
                      type="password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="새 비밀번호를 다시 입력하세요"
                      disabled={changePasswordMutation.isPending}
                      required
                      minLength={6}
                    />
                  </div>

                  <div className="form-actions">
                    <button
                      type="submit"
                      className="btn-primary"
                      disabled={changePasswordMutation.isPending}
                    >
                      {changePasswordMutation.isPending ? '변경 중...' : '비밀번호 변경'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          )}
        </div>

        <div className="mypage-footer">
          <div className="footer-actions">
            <button
              className="btn-secondary"
              onClick={() => navigate('/dashboard')}
            >
              대시보드로 돌아가기
            </button>
            <button
              className="btn-logout"
              onClick={() => {
                if (window.confirm('로그아웃 하시겠습니까?')) {
                  logout();
                  navigate('/login');
                }
              }}
            >
              로그아웃
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MyPage;
