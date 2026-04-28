import axios from 'axios';
import type { ApiResponse, MqConnectionRequest, MqConnectionResponse, MqTestConnectionRequest,
  QueueInfoResponse, MessageSummaryResponse, MessageDetailResponse, AuditLogEntry,
  CoherenceServerRequest, CoherenceServerResponse, CoherenceStatusResponse,
  MskConfigRequest, MskConfigResponse, KafkaTopicInfo, KafkaMessageRecord } from '../types';

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

  // MSK Kafka
  msk: {
    listConfigs: () =>
      api.get<ApiResponse<MskConfigResponse[]>>('/msk/configs'),
    createConfig: (data: MskConfigRequest) =>
      api.post<ApiResponse<MskConfigResponse>>('/msk/configs', data),
    updateConfig: (id: number, data: MskConfigRequest) =>
      api.put<ApiResponse<MskConfigResponse>>(`/msk/configs/${id}`, data),
    deleteConfig: (id: number) =>
      api.delete<ApiResponse<void>>(`/msk/configs/${id}`),
    testConnection: (data: MskConfigRequest) =>
      api.post<ApiResponse<string>>('/msk/configs/test', data),
    listTopics: (configId: number, includeInternal = false) =>
      api.get<ApiResponse<KafkaTopicInfo[]>>(
        `/msk/configs/${configId}/topics?includeInternal=${includeInternal}`
      ),
    browseMessages: (configId: number, topic: string, limit = 50) =>
      api.get<ApiResponse<KafkaMessageRecord[]>>(
        `/msk/configs/${configId}/topics/${encodeURIComponent(topic)}/messages?limit=${limit}`
      ),
  },

  // Coherence servers
  coherence: {
    listServers: () =>
      api.get<ApiResponse<CoherenceServerResponse[]>>('/coherence/servers'),
    createServer: (data: CoherenceServerRequest) =>
      api.post<ApiResponse<CoherenceServerResponse>>('/coherence/servers', data),
    updateServer: (id: number, data: CoherenceServerRequest) =>
      api.put<ApiResponse<CoherenceServerResponse>>(`/coherence/servers/${id}`, data),
    deleteServer: (id: number) =>
      api.delete<ApiResponse<void>>(`/coherence/servers/${id}`),
    checkStatus: (id: number) =>
      api.get<ApiResponse<CoherenceStatusResponse>>(`/coherence/servers/${id}/status`),
    stopService: (id: number) =>
      api.post<ApiResponse<CoherenceStatusResponse>>(`/coherence/servers/${id}/stop`),
    startService: (id: number) =>
      api.post<ApiResponse<CoherenceStatusResponse>>(`/coherence/servers/${id}/start`),
    testSsh: (data: CoherenceServerRequest) =>
      api.post<ApiResponse<string>>('/coherence/servers/test-ssh', data),
  },
};

export default api;
