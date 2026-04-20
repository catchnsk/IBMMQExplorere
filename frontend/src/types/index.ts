export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  mqErrorCode?: number;
  validationErrors?: Record<string, string>;
  timestamp: string;
}

export interface MqConnectionResponse {
  id: number;
  configName: string;
  host: string;
  port: number;
  queueManagerName: string;
  channel: string;
  username?: string;
  hasPassword: boolean;
  sslCipherSpec?: string;
  keystorePath?: string;
  truststorePath?: string;
  sslEnabled: boolean;
  enabled: boolean;
  createdAt: string;
  updatedAt?: string;
  createdBy?: string;
}

export interface MqConnectionRequest {
  configName: string;
  host: string;
  port: number;
  queueManagerName: string;
  channel: string;
  username?: string;
  password?: string;
  keystorePassword?: string;
  sslCipherSpec?: string;
  keystorePath?: string;
  truststorePath?: string;
  sslEnabled?: boolean;
}

export interface MqTestConnectionRequest {
  host: string;
  port: number;
  queueManagerName: string;
  channel: string;
  username?: string;
  password?: string;
  sslCipherSpec?: string;
  sslEnabled?: boolean;
}

export interface QueueInfoResponse {
  name: string;
  type: string;
  currentDepth: number;
  maxDepth: number;
  openInputCount: number;
  openOutputCount: number;
  description?: string;
  getInhibited: boolean;
  putInhibited: boolean;
}

export interface MessageSummaryResponse {
  index: number;
  messageId: string;
  correlationId: string;
  putTimestamp?: string;
  putApplicationName?: string;
  messageType: number;
  expiry: number;
  priority: number;
  persistence: number;
  encoding: number;
  format: string;
  dataLength: number;
}

export interface MessageDetailResponse {
  messageId: string;
  correlationId: string;
  format: string;
  encoding: number;
  codedCharacterSetId: number;
  messageType: number;
  expiry: number;
  priority: number;
  persistence: number;
  replyToQueue?: string;
  replyToQueueManager?: string;
  putApplicationName?: string;
  putDateTime?: string;
  userId?: string;
  rawBodySize: number;
  contentType: string;
  textView?: string;
  jsonView?: string;
  xmlView?: string;
  hexView?: string;
}

export interface AuditLogEntry {
  id: number;
  username: string;
  action: string;
  queueManagerName?: string;
  targetResource?: string;
  outcome: string;
  details?: string;
  timestamp: string;
  clientIp?: string;
}
