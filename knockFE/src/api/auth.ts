import { apiClient } from './client';

export interface AuthRequest {
  username?: string; // 로그인 시 필수, 회원가입 시 필수
  email?: string;    // 회원가입 시에만 필수
  password: string;
  nickname?: string; // 회원가입 시에만 사용
}

export interface AuthResponse {
  token: string;
  username: string;
  email: string;
  name: string;
  userId: number;
}

export interface UserProfile {
  id: number;
  username: string;
  email: string;
  nickname: string;
}

export interface UserUpdateRequest {
  username?: string;
  nickname?: string;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}

export const authAPI = {
  login: async (credentials: AuthRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/login', credentials);
    if (response.data.token) {
      localStorage.setItem('token', response.data.token);
      localStorage.setItem('userId', response.data.userId.toString());
      localStorage.setItem('username', response.data.username);
      localStorage.setItem('email', response.data.email);
      localStorage.setItem('name', response.data.name);
    }
    return response.data;
  },

  register: async (credentials: AuthRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/register', credentials);
    if (response.data.token) {
      localStorage.setItem('token', response.data.token);
      localStorage.setItem('userId', response.data.userId.toString());
      localStorage.setItem('username', response.data.username);
      localStorage.setItem('email', response.data.email);
      localStorage.setItem('name', response.data.name);
    }
    return response.data;
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    localStorage.removeItem('email');
    localStorage.removeItem('name');
  },

  // 이메일 변경 인증 코드 전송
  sendEmailVerificationCode: async (newEmail: string): Promise<{ message: string }> => {
    const response = await apiClient.post<{ message: string }>('/auth/email/verification-code', {
      newEmail,
    });
    return response.data;
  },

  // 이메일 변경
  changeEmail: async (newEmail: string, verificationCode: string, password: string): Promise<{ message: string }> => {
    const response = await apiClient.put<{ message: string }>('/auth/email', {
      newEmail,
      verificationCode,
      password,
    });
    return response.data;
  },

  // 사용자 정보 조회
  getProfile: async (): Promise<UserProfile> => {
    const response = await apiClient.get<UserProfile>('/auth/profile');
    return response.data;
  },

  // 사용자 정보 수정 (아이디, 닉네임)
  updateProfile: async (data: UserUpdateRequest): Promise<UserProfile> => {
    const response = await apiClient.put<UserProfile>('/auth/profile', data);
    return response.data;
  },

  // 비밀번호 변경
  changePassword: async (data: PasswordChangeRequest): Promise<{ message: string }> => {
    const response = await apiClient.put<{ message: string }>('/auth/password', data);
    return response.data;
  },

  // 아이디 변경
  changeUsername: async (newUsername: string, password: string): Promise<UserProfile> => {
    const response = await apiClient.put<UserProfile>('/auth/username', {
      newUsername,
      password,
    });
    return response.data;
  },
};
