import axios from 'axios';
import type { ApiResponse, MqConnectionRequest, MqConnectionResponse, MqTestConnectionRequest,
  QueueInfoResponse, MessageSummaryResponse, MessageDetailResponse, AuditLogEntry } from '../types';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
});

// Redirect to login on 401
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const mqApi = {
  // Auth
  login: (username: string, password: string) => {
    const params = new URLSearchParams({ username, password });
    return api.post<ApiResponse<{ username: string; roles: string[] }>>('/auth/login', params, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    });
  },
  logout: () => api.post<ApiResponse<null>>('/auth/logout'),

  // Config management
  saveConfig: (data: MqConnectionRequest) =>
    api.post<ApiResponse<MqConnectionResponse>>('/mq/config/save', data),
  getAllConfigs: () =>
    api.get<ApiResponse<MqConnectionResponse[]>>('/mq/config/all'),
  getConfig: (id: number) =>
    api.get<ApiResponse<MqConnectionResponse>>(`/mq/config/${id}`),
  deleteConfig: (id: number) =>
    api.delete<ApiResponse<null>>(`/mq/config/${id}`),

  // Connection management
  connect: (configId: number) =>
    api.post<ApiResponse<null>>(`/mq/connect?configId=${configId}`),
  disconnect: (configId: number) =>
    api.post<ApiResponse<null>>(`/mq/disconnect?configId=${configId}`),
  getStatus: (configId: number) =>
    api.get<ApiResponse<{ connected: boolean; configId: number }>>(`/mq/status?configId=${configId}`),
  testConnection: (data: MqTestConnectionRequest) =>
    api.post<ApiResponse<string>>('/mq/test-connection', data),

  // Queues
  listQueues: (configId: number, includeSystemQueues = false) =>
    api.get<ApiResponse<QueueInfoResponse[]>>(
      `/mq/queues?configId=${configId}&includeSystemQueues=${includeSystemQueues}`
    ),

  // Messages
  browseMessages: (configId: number, queueName: string, params: {
    correlationId?: string;
    messageId?: string;
    limit?: number;
  }) =>
    api.get<ApiResponse<MessageSummaryResponse[]>>(
      `/mq/queues/${encodeURIComponent(queueName)}/messages`,
      { params: { configId, ...params } }
    ),
  getMessage: (configId: number, queueName: string, messageId: string) =>
    api.get<ApiResponse<MessageDetailResponse>>(
      `/mq/queues/${encodeURIComponent(queueName)}/messages/${messageId}`,
      { params: { configId } }
    ),

  // Health & audit
  health: () => api.get<ApiResponse<{ status: string; activeConnections: number }>>('/mq/health'),
  getAuditLog: () => api.get<ApiResponse<AuditLogEntry[]>>('/mq/audit'),
};

export default api;
