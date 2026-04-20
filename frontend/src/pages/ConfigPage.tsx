import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2, Plug, PlugZap, TestTube, Save, ChevronDown } from 'lucide-react';
import { mqApi } from '../api/mqApi';
import { useAuthStore } from '../store/authStore';
import { useConnectionStore } from '../store/connectionStore';
import type { MqConnectionRequest, MqConnectionResponse } from '../types';

const defaultForm: MqConnectionRequest = {
  configName: '', host: '', port: 1414,
  queueManagerName: '', channel: 'SYSTEM.DEF.SVRCONN',
  username: '', password: '', sslCipherSpec: '',
  keystorePath: '', truststorePath: '', sslEnabled: false,
};

export default function ConfigPage() {
  const isAdmin = useAuthStore(s => s.isAdmin);
  const { activeConfigId, setConnection, clearConnection } = useConnectionStore();
  const queryClient = useQueryClient();

  const [form, setForm] = useState<MqConnectionRequest>(defaultForm);
  const [status, setStatus] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [showSsl, setShowSsl] = useState(false);

  const { data: configs = [] } = useQuery<MqConnectionResponse[]>({
    queryKey: ['configs'],
    queryFn: () => mqApi.getAllConfigs().then(r => r.data.data),
  });

  const saveMutation = useMutation({
    mutationFn: (data: MqConnectionRequest) => mqApi.saveConfig(data),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['configs'] });
      setStatus({ type: 'success', message: res.data.message ?? 'Configuration saved' });
    },
    onError: (err: any) => {
      setStatus({ type: 'error', message: err.response?.data?.message ?? 'Save failed' });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => mqApi.deleteConfig(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['configs'] });
      setStatus({ type: 'success', message: 'Configuration deleted' });
    },
  });

  const connectMutation = useMutation({
    mutationFn: (configId: number) => mqApi.connect(configId),
    onSuccess: (_, configId) => {
      const config = configs.find(c => c.id === configId);
      if (config) setConnection(config);
      setStatus({ type: 'success', message: 'Connected successfully' });
    },
    onError: (err: any) => {
      setStatus({ type: 'error', message: err.response?.data?.message ?? 'Connection failed' });
    },
  });

  const disconnectMutation = useMutation({
    mutationFn: (configId: number) => mqApi.disconnect(configId),
    onSuccess: () => {
      clearConnection();
      setStatus({ type: 'success', message: 'Disconnected' });
    },
  });

  const testMutation = useMutation({
    mutationFn: () => mqApi.testConnection({
      host: form.host, port: form.port, queueManagerName: form.queueManagerName,
      channel: form.channel, username: form.username, password: form.password,
      sslCipherSpec: form.sslCipherSpec, sslEnabled: form.sslEnabled,
    }),
    onSuccess: (res) => {
      setStatus({
        type: res.data.success ? 'success' : 'error',
        message: res.data.data ?? res.data.message ?? 'Test complete',
      });
    },
    onError: (err: any) => {
      setStatus({ type: 'error', message: err.response?.data?.message ?? 'Test failed' });
    },
  });

  const loadConfig = (config: MqConnectionResponse) => {
    setForm({
      configName: config.configName, host: config.host, port: config.port,
      queueManagerName: config.queueManagerName, channel: config.channel,
      username: config.username ?? '', password: '',
      sslCipherSpec: config.sslCipherSpec ?? '',
      keystorePath: config.keystorePath ?? '', truststorePath: config.truststorePath ?? '',
      sslEnabled: config.sslEnabled,
    });
    setShowSsl(config.sslEnabled);
    setStatus(null);
  };

  const field = (label: string, key: keyof MqConnectionRequest, type = 'text', placeholder = '') => (
    <div>
      <label className="label">{label}</label>
      <input
        type={type}
        className="input-field"
        placeholder={placeholder}
        value={(form[key] as string) ?? ''}
        onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}
      />
    </div>
  );

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h2 className="text-2xl font-semibold text-gray-900 dark:text-white">MQ Configuration</h2>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
          Configure and manage IBM MQ connection profiles
        </p>
      </div>

      {configs.length > 0 && (
        <div className="card p-4">
          <label className="label">Load Saved Configuration</label>
          <div className="flex gap-2">
            <select
              className="input-field"
              defaultValue=""
              onChange={e => {
                const cfg = configs.find(c => String(c.id) === e.target.value);
                if (cfg) loadConfig(cfg);
              }}
            >
              <option value="">— Select a configuration —</option>
              {configs.map(c => (
                <option key={c.id} value={c.id}>
                  {c.configName} ({c.host}:{c.port} / {c.queueManagerName})
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      <div className="card p-6 space-y-4">
        <h3 className="text-base font-semibold text-gray-800 dark:text-gray-100">Connection Details</h3>

        {field('Configuration Name', 'configName', 'text', 'e.g., Production QM1')}

        <div className="grid grid-cols-2 gap-4">
          {field('Host / Server', 'host', 'text', 'e.g., mq.example.com')}
          <div>
            <label className="label">Port</label>
            <input type="number" className="input-field" min={1} max={65535}
              value={form.port} onChange={e => setForm(f => ({ ...f, port: Number(e.target.value) }))} />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          {field('Queue Manager Name', 'queueManagerName', 'text', 'e.g., QM1')}
          {field('Channel Name', 'channel', 'text', 'e.g., SYSTEM.DEF.SVRCONN')}
        </div>

        <div className="grid grid-cols-2 gap-4">
          {field('Username', 'username', 'text', 'Optional')}
          {field('Password', 'password', 'password', form.configName ? '(leave blank to keep existing)' : '')}
        </div>

        <div>
          <button
            onClick={() => setShowSsl(!showSsl)}
            className="flex items-center gap-2 text-sm text-blue-600 dark:text-blue-400 hover:underline"
          >
            <ChevronDown className={`w-4 h-4 transition-transform ${showSsl ? 'rotate-180' : ''}`} />
            SSL/TLS Settings {showSsl ? '(hide)' : '(show)'}
          </button>
          {showSsl && (
            <div className="mt-3 space-y-3 pl-4 border-l-2 border-gray-200 dark:border-gray-600">
              <div className="flex items-center gap-2">
                <input type="checkbox" id="sslEnabled" checked={form.sslEnabled}
                  onChange={e => setForm(f => ({ ...f, sslEnabled: e.target.checked }))} />
                <label htmlFor="sslEnabled" className="text-sm text-gray-700 dark:text-gray-300">Enable SSL/TLS</label>
              </div>
              {field('Cipher Suite', 'sslCipherSpec', 'text', 'e.g., TLS_RSA_WITH_AES_256_CBC_SHA256')}
              {field('Keystore Path', 'keystorePath', 'text', '/path/to/keystore.jks')}
              {field('Truststore Path', 'truststorePath', 'text', '/path/to/truststore.jks')}
            </div>
          )}
        </div>
      </div>

      {status && (
        <div className={`p-4 rounded border text-sm ${
          status.type === 'success'
            ? 'bg-green-50 dark:bg-green-900/20 border-green-300 dark:border-green-700 text-green-700 dark:text-green-300'
            : 'bg-red-50 dark:bg-red-900/20 border-red-300 dark:border-red-700 text-red-700 dark:text-red-300'
        }`}>
          {status.message}
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        {isAdmin() && (
          <>
            <button
              className="btn-secondary flex items-center gap-2"
              disabled={testMutation.isPending}
              onClick={() => testMutation.mutate()}
            >
              <TestTube className="w-4 h-4" />
              {testMutation.isPending ? 'Testing...' : 'Test Connection'}
            </button>
            <button
              className="btn-primary flex items-center gap-2"
              disabled={saveMutation.isPending}
              onClick={() => saveMutation.mutate(form)}
            >
              <Save className="w-4 h-4" />
              {saveMutation.isPending ? 'Saving...' : 'Save Configuration'}
            </button>
          </>
        )}

        {activeConfigId ? (
          <button
            className="btn-danger flex items-center gap-2"
            disabled={disconnectMutation.isPending}
            onClick={() => disconnectMutation.mutate(activeConfigId)}
          >
            <PlugZap className="w-4 h-4" />
            {disconnectMutation.isPending ? 'Disconnecting...' : 'Disconnect'}
          </button>
        ) : (
          <button
            className="btn-primary flex items-center gap-2 bg-green-600 hover:bg-green-700"
            disabled={connectMutation.isPending || !form.host}
            onClick={() => {
              const cfg = configs.find(c => c.configName === form.configName);
              if (cfg) connectMutation.mutate(cfg.id);
              else setStatus({ type: 'error', message: 'Save the configuration first, then connect.' });
            }}
          >
            <Plug className="w-4 h-4" />
            {connectMutation.isPending ? 'Connecting...' : 'Connect'}
          </button>
        )}
      </div>

      {isAdmin() && configs.length > 0 && (
        <div className="card">
          <div className="px-5 py-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-200">Saved Configurations</h3>
          </div>
          <div className="divide-y divide-gray-100 dark:divide-gray-700">
            {configs.map(cfg => (
              <div key={cfg.id} className="px-5 py-3 flex items-center justify-between">
                <div>
                  <div className="text-sm font-medium text-gray-900 dark:text-white">{cfg.configName}</div>
                  <div className="text-xs text-gray-500">
                    {cfg.host}:{cfg.port} — {cfg.queueManagerName} — {cfg.channel}
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => loadConfig(cfg)}
                    className="text-xs text-blue-600 dark:text-blue-400 hover:underline"
                  >
                    Load
                  </button>
                  <button
                    onClick={() => {
                      if (window.confirm(`Delete "${cfg.configName}"?`)) {
                        deleteMutation.mutate(cfg.id);
                      }
                    }}
                    className="text-xs text-red-600 dark:text-red-400 hover:underline"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
