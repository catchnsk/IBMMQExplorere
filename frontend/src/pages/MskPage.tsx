import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Layers, Plus, Pencil, Trash2, RefreshCw, ChevronRight, X,
  TestTube, Save, MessageSquare, Hash, Copy, CheckCheck,
} from 'lucide-react';
import { mqApi } from '../api/mqApi';
import { useAuthStore } from '../store/authStore';
import type {
  MskConfigRequest, MskConfigResponse, KafkaTopicInfo, KafkaMessageRecord, MskAuthType,
} from '../types';

const AUTH_LABELS: Record<MskAuthType, string> = {
  NONE: 'None (Plaintext)',
  SSL: 'SSL only',
  SASL_SCRAM: 'SASL/SCRAM (username + password)',
  IAM: 'AWS MSK IAM',
};

const defaultForm: MskConfigRequest = {
  configName: '', bootstrapServers: '', awsRegion: '',
  authType: 'NONE', saslUsername: '', saslPassword: '',
  accessKey: '', secretKey: '', sessionToken: '',
};

// ── Config modal ─────────────────────────────────────────────────────────────

function ConfigModal({
  initial, onClose, onSave,
}: {
  initial: MskConfigResponse | null;
  onClose: () => void;
  onSave: (req: MskConfigRequest, id?: number) => void;
}) {
  const isEdit = !!initial;
  const [form, setForm] = useState<MskConfigRequest>(
    initial
      ? {
          configName: initial.configName,
          bootstrapServers: initial.bootstrapServers,
          awsRegion: initial.awsRegion ?? '',
          authType: initial.authType,
          saslUsername: initial.saslUsername ?? '',
          saslPassword: '', accessKey: '', secretKey: '', sessionToken: '',
        }
      : { ...defaultForm }
  );
  const [testResult, setTestResult] = useState<{ ok: boolean; msg: string } | null>(null);
  const [testing, setTesting] = useState(false);

  const set = <K extends keyof MskConfigRequest>(k: K, v: MskConfigRequest[K]) =>
    setForm(f => ({ ...f, [k]: v }));

  const handleTest = async () => {
    setTesting(true); setTestResult(null);
    try {
      const res = await mqApi.msk.testConnection(form);
      setTestResult({ ok: res.data.success, msg: res.data.message ?? res.data.data ?? '' });
    } catch (e: any) {
      setTestResult({ ok: false, msg: e.response?.data?.message ?? 'Test failed' });
    } finally { setTesting(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl w-full max-w-lg">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200 dark:border-gray-700">
          <h3 className="font-semibold text-gray-900 dark:text-white">
            {isEdit ? 'Edit MSK Config' : 'Add MSK Config'}
          </h3>
          <button onClick={onClose}><X className="w-5 h-5 text-gray-400 hover:text-gray-600" /></button>
        </div>

        <div className="p-5 space-y-4 max-h-[70vh] overflow-y-auto">
          <div>
            <label className="label">Config Name</label>
            <input className="input-field" value={form.configName}
              onChange={e => set('configName', e.target.value)} placeholder="e.g., Prod MSK" />
          </div>
          <div>
            <label className="label">Bootstrap Servers</label>
            <input className="input-field" value={form.bootstrapServers}
              onChange={e => set('bootstrapServers', e.target.value)}
              placeholder="broker1:9098,broker2:9098" />
            <p className="text-xs text-gray-400 mt-1">Comma-separated host:port pairs</p>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">AWS Region</label>
              <input className="input-field" value={form.awsRegion ?? ''}
                onChange={e => set('awsRegion', e.target.value)} placeholder="e.g., us-east-1" />
            </div>
            <div>
              <label className="label">Auth Type</label>
              <select className="input-field" value={form.authType}
                onChange={e => set('authType', e.target.value as MskAuthType)}>
                {(Object.keys(AUTH_LABELS) as MskAuthType[]).map(k => (
                  <option key={k} value={k}>{AUTH_LABELS[k]}</option>
                ))}
              </select>
            </div>
          </div>

          {form.authType === 'SASL_SCRAM' && (
            <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-700/40 rounded">
              <p className="text-xs font-medium text-gray-600 dark:text-gray-400">SCRAM Credentials</p>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label">Username</label>
                  <input className="input-field" value={form.saslUsername ?? ''}
                    onChange={e => set('saslUsername', e.target.value)} />
                </div>
                <div>
                  <label className="label">
                    Password {isEdit && <span className="text-gray-400 font-normal">(blank = keep)</span>}
                  </label>
                  <input className="input-field" type="password" value={form.saslPassword ?? ''}
                    onChange={e => set('saslPassword', e.target.value)} />
                </div>
              </div>
            </div>
          )}

          {form.authType === 'IAM' && (
            <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-700/40 rounded">
              <p className="text-xs font-medium text-gray-600 dark:text-gray-400">
                IAM Credentials
                <span className="ml-1 font-normal text-gray-400">
                  — leave blank to use env/instance profile
                </span>
              </p>
              <div>
                <label className="label">
                  Access Key ID {isEdit && initial?.hasIamCredentials && <span className="text-gray-400 font-normal">(blank = keep)</span>}
                </label>
                <input className="input-field" value={form.accessKey ?? ''}
                  onChange={e => set('accessKey', e.target.value)}
                  placeholder="AKIA..." />
              </div>
              <div>
                <label className="label">Secret Access Key</label>
                <input className="input-field" type="password" value={form.secretKey ?? ''}
                  onChange={e => set('secretKey', e.target.value)} />
              </div>
              <div>
                <label className="label">
                  Session Token&nbsp;
                  <span className="text-gray-400 font-normal">
                    — required for STS / SSO / assumed-role temporary credentials
                    {isEdit && initial?.hasSessionToken && ' (blank = keep)'}
                  </span>
                </label>
                <input className="input-field font-mono text-xs" type="password"
                  value={form.sessionToken ?? ''}
                  onChange={e => set('sessionToken', e.target.value)}
                  placeholder="leave blank for long-term IAM credentials" />
              </div>
            </div>
          )}

          {testResult && (
            <div className={`p-3 rounded text-sm ${testResult.ok
              ? 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300'
              : 'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300'}`}>
              {testResult.msg}
            </div>
          )}
        </div>

        <div className="flex justify-between px-5 py-4 border-t border-gray-200 dark:border-gray-700">
          <button onClick={handleTest} disabled={testing || !form.bootstrapServers}
            className="btn-secondary flex items-center gap-2 text-sm">
            <TestTube className="w-4 h-4" />
            {testing ? 'Testing…' : 'Test Connection'}
          </button>
          <div className="flex gap-3">
            <button onClick={onClose} className="btn-secondary text-sm">Cancel</button>
            <button onClick={() => onSave(form, initial?.id)}
              disabled={!form.configName || !form.bootstrapServers}
              className="btn-primary flex items-center gap-2 text-sm">
              <Save className="w-4 h-4" />
              {isEdit ? 'Update' : 'Save'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Message viewer ────────────────────────────────────────────────────────────

function MessageViewer({
  topic, messages, loading, onRefresh, limit, onLimitChange,
}: {
  topic: string;
  messages: KafkaMessageRecord[];
  loading: boolean;
  onRefresh: () => void;
  limit: number;
  onLimitChange: (n: number) => void;
}) {
  const [selected, setSelected] = useState<KafkaMessageRecord | null>(null);
  const [copied, setCopied] = useState(false);

  const copyValue = (v: string) => {
    navigator.clipboard.writeText(v);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const formatJson = (v: string) => {
    try { return JSON.stringify(JSON.parse(v), null, 2); }
    catch { return v; }
  };

  return (
    <div className="flex flex-col h-full">
      {/* toolbar */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 flex-shrink-0">
        <div className="flex items-center gap-2 min-w-0">
          <MessageSquare className="w-4 h-4 text-blue-500 flex-shrink-0" />
          <span className="text-sm font-medium text-gray-800 dark:text-gray-200 truncate" title={topic}>
            {topic}
          </span>
          <span className="text-xs text-gray-400">({messages.length} records)</span>
        </div>
        <div className="flex items-center gap-3 flex-shrink-0">
          <div className="flex items-center gap-1.5">
            <label className="text-xs text-gray-500">Limit</label>
            <select className="text-xs border border-gray-300 dark:border-gray-600 rounded px-1.5 py-0.5
                bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300"
              value={limit} onChange={e => onLimitChange(Number(e.target.value))}>
              {[25, 50, 100, 200, 500].map(n => (
                <option key={n} value={n}>{n}</option>
              ))}
            </select>
          </div>
          <button onClick={onRefresh} disabled={loading}
            className="btn-secondary text-xs py-1 px-2 flex items-center gap-1">
            <RefreshCw className={`w-3 h-3 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* two-panel: list + detail */}
      <div className="flex flex-1 min-h-0">
        {/* message list */}
        <div className="w-64 flex-shrink-0 border-r border-gray-200 dark:border-gray-700 overflow-y-auto">
          {loading ? (
            <div className="p-4 text-center text-sm text-gray-400">
              <RefreshCw className="w-5 h-5 animate-spin mx-auto mb-2" /> Loading…
            </div>
          ) : messages.length === 0 ? (
            <div className="p-4 text-center text-sm text-gray-400">No messages</div>
          ) : (
            messages.map((m, i) => (
              <div
                key={i}
                onClick={() => setSelected(m)}
                className={`px-3 py-2 cursor-pointer border-b border-gray-100 dark:border-gray-800 text-xs
                  ${selected === m
                    ? 'bg-blue-50 dark:bg-blue-900/30'
                    : 'hover:bg-gray-50 dark:hover:bg-gray-700/50'}`}
              >
                <div className="flex items-center justify-between gap-1 mb-0.5">
                  <span className="font-mono text-gray-500">P{m.partition}:{m.offset}</span>
                  <span className={`px-1 py-0.5 rounded text-xs font-medium ${
                    m.valueType === 'JSON'   ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400' :
                    m.valueType === 'BINARY' ? 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400' :
                    'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400'
                  }`}>{m.valueType}</span>
                </div>
                {m.key && <div className="text-blue-600 dark:text-blue-400 truncate">{m.key}</div>}
                {m.timestamp && <div className="text-gray-400 text-xs">{m.timestamp}</div>}
                <div className="text-gray-400 text-xs">{m.valueSize} B</div>
              </div>
            ))
          )}
        </div>

        {/* message detail */}
        <div className="flex-1 overflow-auto p-4">
          {!selected ? (
            <div className="h-full flex items-center justify-center text-gray-400 text-sm">
              Select a message to view details
            </div>
          ) : (
            <div className="space-y-4">
              {/* metadata */}
              <div className="card p-4">
                <h4 className="text-xs font-semibold text-gray-600 dark:text-gray-400 mb-3 uppercase tracking-wide">
                  Metadata
                </h4>
                <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
                  {[
                    ['Partition', `${selected.partition}`],
                    ['Offset', `${selected.offset}`],
                    ['Timestamp', selected.timestamp ?? '—'],
                    ['Key', selected.key ?? '(null)'],
                    ['Size', `${selected.valueSize} bytes`],
                    ['Type', selected.valueType],
                  ].map(([k, v]) => (
                    <div key={k}>
                      <span className="text-gray-500 dark:text-gray-400">{k}: </span>
                      <span className="text-gray-800 dark:text-gray-200 font-mono">{v}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* headers */}
              {selected.headers && Object.keys(selected.headers).length > 0 && (
                <div className="card p-4">
                  <h4 className="text-xs font-semibold text-gray-600 dark:text-gray-400 mb-2 uppercase tracking-wide">
                    Headers
                  </h4>
                  <div className="space-y-1">
                    {Object.entries(selected.headers).map(([k, v]) => (
                      <div key={k} className="text-xs font-mono">
                        <span className="text-purple-600 dark:text-purple-400">{k}</span>
                        <span className="text-gray-400 mx-1">=</span>
                        <span className="text-gray-700 dark:text-gray-300">{v}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* value */}
              <div className="card p-4">
                <div className="flex items-center justify-between mb-2">
                  <h4 className="text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase tracking-wide">
                    Value
                  </h4>
                  {selected.value && (
                    <button onClick={() => copyValue(selected.value!)}
                      className="text-xs text-blue-500 hover:text-blue-700 flex items-center gap-1">
                      {copied ? <CheckCheck className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                      {copied ? 'Copied' : 'Copy'}
                    </button>
                  )}
                </div>
                {selected.value ? (
                  <pre className="text-xs font-mono text-gray-800 dark:text-gray-200 bg-gray-50
                    dark:bg-gray-900 rounded p-3 overflow-auto max-h-80 whitespace-pre-wrap break-all">
                    {selected.valueType === 'JSON' ? formatJson(selected.value) : selected.value}
                  </pre>
                ) : (
                  <span className="text-xs text-gray-400 italic">null</span>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function MskPage() {
  const isAdmin = useAuthStore(s => s.isAdmin);
  const queryClient = useQueryClient();

  const [selectedConfigId, setSelectedConfigId] = useState<number | null>(null);
  const [selectedTopic, setSelectedTopic] = useState<string | null>(null);
  const [modal, setModal] = useState<MskConfigResponse | null | 'new'>(null);
  const [messageLimit, setMessageLimit] = useState(50);
  const [includeInternal, setIncludeInternal] = useState(false);
  const [topicSearch, setTopicSearch] = useState('');
  const [toast, setToast] = useState<{ ok: boolean; msg: string } | null>(null);

  const showToast = (ok: boolean, msg: string) => {
    setToast({ ok, msg });
    setTimeout(() => setToast(null), 4000);
  };

  const { data: configs = [] } = useQuery<MskConfigResponse[]>({
    queryKey: ['msk-configs'],
    queryFn: () => mqApi.msk.listConfigs().then(r => r.data.data),
  });

  const { data: topics = [], isFetching: topicsLoading, refetch: refetchTopics } =
    useQuery<KafkaTopicInfo[]>({
      queryKey: ['msk-topics', selectedConfigId, includeInternal],
      queryFn: () => mqApi.msk.listTopics(selectedConfigId!, includeInternal)
        .then(r => {
          if (!r.data.success) throw new Error(r.data.message);
          return r.data.data;
        }),
      enabled: selectedConfigId !== null,
      retry: false,
    });

  const { data: messages = [], isFetching: msgsLoading, refetch: refetchMessages } =
    useQuery<KafkaMessageRecord[]>({
      queryKey: ['msk-messages', selectedConfigId, selectedTopic, messageLimit],
      queryFn: () => mqApi.msk.browseMessages(selectedConfigId!, selectedTopic!, messageLimit)
        .then(r => {
          if (!r.data.success) throw new Error(r.data.message);
          return r.data.data;
        }),
      enabled: selectedConfigId !== null && selectedTopic !== null,
      retry: false,
    });

  const saveMutation = useMutation({
    mutationFn: ({ req, id }: { req: MskConfigRequest; id?: number }) =>
      id ? mqApi.msk.updateConfig(id, req) : mqApi.msk.createConfig(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['msk-configs'] });
      setModal(null);
      showToast(true, 'Configuration saved');
    },
    onError: (e: any) => showToast(false, e.response?.data?.message ?? 'Save failed'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => mqApi.msk.deleteConfig(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['msk-configs'] });
      if (selectedConfigId === id) { setSelectedConfigId(null); setSelectedTopic(null); }
      showToast(true, 'Configuration removed');
    },
  });

  const handleSelectConfig = (id: number) => {
    setSelectedConfigId(id === selectedConfigId ? null : id);
    setSelectedTopic(null);
  };

  const filteredTopics = topics.filter(t =>
    t.name.toLowerCase().includes(topicSearch.toLowerCase())
  );

  const selectedConfig = configs.find(c => c.id === selectedConfigId);

  return (
    <div className="flex flex-col h-full space-y-0">
      {/* Page header */}
      <div className="flex items-center justify-between pb-4 flex-shrink-0">
        <div>
          <h2 className="text-2xl font-semibold text-gray-900 dark:text-white flex items-center gap-2">
            <Layers className="w-6 h-6 text-orange-500" />
            MSK Kafka Explorer
          </h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Browse topics and messages on Amazon MSK clusters
          </p>
        </div>
        {isAdmin() && (
          <button onClick={() => setModal('new')} className="btn-primary flex items-center gap-2 text-sm">
            <Plus className="w-4 h-4" /> Add Config
          </button>
        )}
      </div>

      {/* Three-panel layout */}
      <div className="flex flex-1 min-h-0 gap-4" style={{ height: 'calc(100vh - 200px)' }}>

        {/* Panel 1 — Connections */}
        <div className="w-52 flex-shrink-0 flex flex-col">
          <div className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2 px-1">
            Connections
          </div>
          <div className="card flex-1 overflow-y-auto">
            {configs.length === 0 ? (
              <div className="p-4 text-center text-xs text-gray-400">
                No configs yet.{isAdmin() && (
                  <button onClick={() => setModal('new')}
                    className="block mt-1 text-blue-500 hover:underline mx-auto">
                    Add one
                  </button>
                )}
              </div>
            ) : (
              <div className="divide-y divide-gray-100 dark:divide-gray-700">
                {configs.map(cfg => (
                  <div
                    key={cfg.id}
                    onClick={() => handleSelectConfig(cfg.id)}
                    className={`px-3 py-2.5 cursor-pointer transition-colors ${
                      selectedConfigId === cfg.id
                        ? 'bg-orange-50 dark:bg-orange-900/20 border-l-2 border-orange-500'
                        : 'hover:bg-gray-50 dark:hover:bg-gray-700/50'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-1">
                      <div className="min-w-0">
                        <div className="text-xs font-medium text-gray-800 dark:text-gray-200 truncate">
                          {cfg.configName}
                        </div>
                        <div className="text-xs text-gray-400 truncate">{cfg.authType}</div>
                        {cfg.awsRegion && (
                          <div className="text-xs text-gray-400">{cfg.awsRegion}</div>
                        )}
                      </div>
                      {selectedConfigId === cfg.id && (
                        <ChevronRight className="w-3.5 h-3.5 text-orange-500 flex-shrink-0" />
                      )}
                    </div>
                    {isAdmin() && (
                      <div className="flex gap-2 mt-1.5" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setModal(cfg)}
                          className="text-xs text-blue-500 hover:underline flex items-center gap-0.5">
                          <Pencil className="w-2.5 h-2.5" /> Edit
                        </button>
                        <button onClick={() => {
                          if (window.confirm(`Delete "${cfg.configName}"?`))
                            deleteMutation.mutate(cfg.id);
                        }} className="text-xs text-red-500 hover:underline flex items-center gap-0.5">
                          <Trash2 className="w-2.5 h-2.5" /> Del
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Panel 2 — Topics */}
        <div className="w-72 flex-shrink-0 flex flex-col">
          <div className="flex items-center justify-between mb-2 px-1">
            <span className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
              Topics {topics.length > 0 && `(${filteredTopics.length})`}
            </span>
            {selectedConfigId && (
              <button onClick={() => refetchTopics()} disabled={topicsLoading}
                className="text-gray-400 hover:text-gray-600">
                <RefreshCw className={`w-3.5 h-3.5 ${topicsLoading ? 'animate-spin' : ''}`} />
              </button>
            )}
          </div>
          <div className="card flex-1 overflow-hidden flex flex-col">
            {!selectedConfigId ? (
              <div className="flex-1 flex items-center justify-center text-xs text-gray-400 p-4 text-center">
                Select a connection to view topics
              </div>
            ) : (
              <>
                <div className="p-2 border-b border-gray-100 dark:border-gray-700 space-y-2">
                  <input
                    className="input-field text-xs py-1"
                    placeholder="Search topics…"
                    value={topicSearch}
                    onChange={e => setTopicSearch(e.target.value)}
                  />
                  <label className="flex items-center gap-1.5 text-xs text-gray-500 cursor-pointer">
                    <input type="checkbox" checked={includeInternal}
                      onChange={e => setIncludeInternal(e.target.checked)} />
                    Include internal topics
                  </label>
                </div>
                <div className="flex-1 overflow-y-auto">
                  {topicsLoading ? (
                    <div className="p-4 text-center text-xs text-gray-400">
                      <RefreshCw className="w-4 h-4 animate-spin mx-auto mb-1" /> Loading topics…
                    </div>
                  ) : filteredTopics.length === 0 ? (
                    <div className="p-4 text-center text-xs text-gray-400">No topics found</div>
                  ) : (
                    filteredTopics.map(t => (
                      <div
                        key={t.name}
                        onClick={() => setSelectedTopic(t.name === selectedTopic ? null : t.name)}
                        className={`px-3 py-2 cursor-pointer border-b border-gray-100 dark:border-gray-800 text-xs
                          ${selectedTopic === t.name
                            ? 'bg-orange-50 dark:bg-orange-900/20 border-l-2 border-orange-500'
                            : 'hover:bg-gray-50 dark:hover:bg-gray-700/50'}`}
                      >
                        <div className="font-medium text-gray-800 dark:text-gray-200 truncate" title={t.name}>
                          {t.name}
                        </div>
                        <div className="flex items-center gap-3 mt-0.5 text-gray-400">
                          <span className="flex items-center gap-0.5">
                            <Hash className="w-2.5 h-2.5" />{t.partitions}p
                          </span>
                          <span className="flex items-center gap-0.5">
                            <MessageSquare className="w-2.5 h-2.5" />
                            {t.totalMessages.toLocaleString()}
                          </span>
                          {t.internal && (
                            <span className="text-yellow-500">internal</span>
                          )}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </>
            )}
          </div>
        </div>

        {/* Panel 3 — Messages */}
        <div className="flex-1 min-w-0 flex flex-col">
          <div className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2 px-1">
            Messages
          </div>
          <div className="card flex-1 overflow-hidden flex flex-col">
            {!selectedTopic ? (
              <div className="flex-1 flex items-center justify-center text-sm text-gray-400 text-center p-8">
                <div>
                  <MessageSquare className="w-10 h-10 mx-auto mb-3 opacity-30" />
                  Select a topic to browse messages
                </div>
              </div>
            ) : (
              <MessageViewer
                topic={selectedTopic}
                messages={messages}
                loading={msgsLoading}
                onRefresh={() => refetchMessages()}
                limit={messageLimit}
                onLimitChange={n => { setMessageLimit(n); }}
              />
            )}
          </div>
        </div>
      </div>

      {/* Toast */}
      {toast && (
        <div className={`fixed bottom-4 right-4 z-50 px-4 py-3 rounded shadow-lg text-sm text-white ${
          toast.ok ? 'bg-green-600' : 'bg-red-600'}`}>
          {toast.msg}
        </div>
      )}

      {/* Config modal */}
      {modal !== null && (
        <ConfigModal
          initial={modal === 'new' ? null : modal}
          onClose={() => setModal(null)}
          onSave={(req, id) => saveMutation.mutate({ req, id })}
        />
      )}
    </div>
  );
}
