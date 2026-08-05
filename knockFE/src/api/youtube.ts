import { apiClient } from './client';

export interface YoutubeVideoAnalysisDto {
  summary: string;
  keyStocks: string[];
  forecastPeriod: string;
  sentiment: string;
  aiComment: string;
  analyzedAt?: string;
}

export interface YoutubeVideoDto {
  id: number;
  videoId: string;
  title: string;
  url: string;
  publishedAt: string;
  thumbnailUrl?: string;
  channelName: string;
  channelYoutubeId?: string;
  category?: string;
  analysisStatus?: string;
  analysis?: YoutubeVideoAnalysisDto;
}

export interface YoutubeChannelDto {
  id: number;
  channelId: string;
  channelName: string;
  category?: string;
  isActive: boolean;
}

export const youtubeAPI = {
  getChannels: async (): Promise<YoutubeChannelDto[]> => {
    const response = await apiClient.get<YoutubeChannelDto[]>('/youtube/channels');
    return response.data;
  },

  getBriefings: async (channelId?: number, days: number = 7): Promise<YoutubeVideoDto[]> => {
    const params = new URLSearchParams();
    params.set('days', String(days));
    if (channelId != null) {
      params.set('channelId', String(channelId));
    }
    const response = await apiClient.get<YoutubeVideoDto[]>(`/youtube/briefings?${params.toString()}`);
    return response.data;
  },

  getBriefingById: async (videoId: number): Promise<YoutubeVideoDto> => {
    const response = await apiClient.get<YoutubeVideoDto>(`/youtube/briefings/${videoId}`);
    return response.data;
  },
};
