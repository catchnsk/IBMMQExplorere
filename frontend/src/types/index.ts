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
  monitoredQueueNames?: string;
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
  monitoredQueueNames?: string;
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

// ── MSK Kafka types ────────────────────────────────────────────────────────

export type MskAuthType = 'NONE' | 'SSL' | 'SASL_SCRAM' | 'IAM';

export interface MskConfigRequest {
  configName: string;
  bootstrapServers: string;
  awsRegion?: string;
  authType: MskAuthType;
  saslUsername?: string;
  saslPassword?: string;
  accessKey?: string;
  secretKey?: string;
  sessionToken?: string;
}

export interface MskConfigResponse {
  id: number;
  configName: string;
  bootstrapServers: string;
  awsRegion?: string;
  authType: MskAuthType;
  saslUsername?: string;
  hasSaslPassword: boolean;
  hasIamCredentials: boolean;
  hasSessionToken: boolean;
  enabled: boolean;
  createdAt: string;
  createdBy?: string;
}

export interface KafkaTopicInfo {
  name: string;
  partitions: number;
  replicationFactor: number;
  totalMessages: number;
  latestOffset: number;
  internal: boolean;
}

export interface KafkaMessageRecord {
  partition: number;
  offset: number;
  timestamp?: string;
  key?: string;
  value?: string;
  valueType: 'TEXT' | 'JSON' | 'BINARY';
  valueSize: number;
  headers?: Record<string, string>;
}

// ── Coherence types ────────────────────────────────────────────────────────

export type CoherenceEnvironment = 'DEV' | 'QA' | 'QA03' | 'PERF';
export type CoherenceServerType = 'CORE_CACHE' | 'DB_SERVER';
export type CoherenceServiceStatus = 'RUNNING' | 'STOPPED' | 'UNKNOWN' | 'ERROR';

export interface CoherenceServerResponse {
  id: number;
  displayName: string;
  host: string;
  sshPort: number;
  username: string;
  hasPassword: boolean;
  environment: CoherenceEnvironment;
  serverType: CoherenceServerType;
  scriptBasePath: string;
  scriptInstance: string;
  scriptDir: string;   // computed by backend: {scriptBasePath}/{scriptInstance}/bin
  enabled: boolean;
  createdAt: string;
  createdBy?: string;
}

export interface CoherenceServerRequest {
  displayName: string;
  host: string;
  sshPort?: number;
  username: string;
  password?: string;
  environment: CoherenceEnvironment;
  serverType: CoherenceServerType;
  scriptBasePath?: string;
  scriptInstance?: string;
}

export interface CoherenceStatusResponse {
  serverId: number;
  host: string;
  status: CoherenceServiceStatus;
  details?: string;
  checkedAt: string;
}

// ── AMQ ActiveMQ types ─────────────────────────────────────────────────────

export type AmqEnvironment = 'QA' | 'QA03' | 'PERF';
export type AmqGroupCategory = 'GROUP_A' | 'GROUP_B';
export type AmqBrokerType = 'CLASSIC' | 'ARTEMIS';

export interface AmqServerRequest {
  displayName: string;
  host: string;
  managementPort?: number;
  username?: string;
  password?: string;
  sshPort?: number;
  sshUsername?: string;
  sshPassword?: string;
  instanceUser?: string;
  instanceName?: string;
  environment: AmqEnvironment;
  groupCategory: AmqGroupCategory;
  brokerType?: AmqBrokerType;
  useSsl?: boolean;
}

export interface AmqServerResponse {
  id: number;
  displayName: string;
  host: string;
  managementPort: number;
  username?: string;
  hasPassword: boolean;
  sshPort: number;
  sshUsername?: string;
  hasSshPassword: boolean;
  instanceUser?: string;
  instanceName?: string;
  binDir: string;
  environment: AmqEnvironment;
  groupCategory: AmqGroupCategory;
  brokerType: AmqBrokerType;
  useSsl: boolean;
  enabled: boolean;
  createdAt: string;
  createdBy?: string;
}

export type AmqServiceStatus = 'RUNNING' | 'STOPPED' | 'UNKNOWN' | 'ERROR';

export interface AmqStatusResponse {
  serverId: number;
  host: string;
  status: AmqServiceStatus;
  details?: string;
  checkedAt: string;
}

export interface AmqQueueInfo {
  name: string;
  queueSize?: number;
  consumerCount?: number;
  producerCount?: number;
  enqueueCount?: number;
  dequeueCount?: number;
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
