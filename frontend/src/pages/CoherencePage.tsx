import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Plus, Pencil, Trash2, RefreshCw, Square, Play,
  CheckCircle2, XCircle, AlertCircle, HelpCircle, TestTube, X, ServerCog,
} from 'lucide-react';
import { mqApi } from '../api/mqApi';
import { useAuthStore } from '../store/authStore';
import type {
  CoherenceServerResponse, CoherenceServerRequest, CoherenceStatusResponse,
  CoherenceEnvironment, CoherenceServerType,
} from '../types';

const ENVIRONMENTS: CoherenceEnvironment[] = ['DEV', 'QA', 'QA03', 'PERF'];
const SERVER_TYPE_LABELS: Record<CoherenceServerType, string> = {
  CORE_CACHE: 'Core Cache Servers',
  DB_SERVER: 'DB Servers',
};
const SERVER_TYPES: CoherenceServerType[] = ['CORE_CACHE', 'DB_SERVER'];

const DEFAULT_SCRIPT_BASE = '/apps/bwag/applications/coherence';
const DEFAULT_SCRIPT_INSTANCE = '999';

const defaultForm: CoherenceServerRequest = {
  displayName: '',
  host: '',
  sshPort: 22,
  username: 'syscouat',
  password: '',
  environment: 'DEV',
  serverType: 'CORE_CACHE',
  scriptBasePath: DEFAULT_SCRIPT_BASE,
  scriptInstance: DEFAULT_SCRIPT_INSTANCE,
};

// ── Status badge ──────────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: CoherenceStatusResponse['status'] | undefined }) {
  if (!status) return (
    <span className="text-xs text-gray-400 flex items-center gap-1">
      <HelpCircle className="w-3.5 h-3.5" /> Unknown
    </span>
  );
  const map = {
    RUNNING: { icon: CheckCircle2, color: 'text-green-500', label: 'Running' },
    STOPPED: { icon: XCircle,      color: 'text-red-500',   label: 'Stopped' },
    UNKNOWN: { icon: HelpCircle,   color: 'text-yellow-500',label: 'Unknown' },
    ERROR:   { icon: AlertCircle,  color: 'text-orange-500',label: 'Error'   },
  } as const;
  const { icon: Icon, color, label } = map[status] ?? map.UNKNOWN;
  return (
    <span className={`text-xs flex items-center gap-1 font-medium ${color}`}>
      <Icon className="w-3.5 h-3.5" /> {label}
    </span>
  );
}

// ── Server card ───────────────────────────────────────────────────────────────

function ServerCard({
  server, statusMap, checkingIds, actionPending,
  onCheck, onStop, onStart, onEdit, onDelete, isAdmin,
}: {
  server: CoherenceServerResponse;
  statusMap: Record<number, CoherenceStatusResponse>;
  checkingIds: Set<number>;
  actionPending: number | null;
  onCheck: (id: number) => void;
  onStop: (id: number) => void;
  onStart: (id: number) => void;
  onEdit: (s: CoherenceServerResponse) => void;
  onDelete: (id: number) => void;
  isAdmin: boolean;
}) {
  const status = statusMap[server.id];
  const isChecking = checkingIds.has(server.id);
  const isPending = actionPending === server.id;

  return (
    <div className="card p-4 space-y-3">
      {/* header row */}
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="text-sm font-semibold text-gray-900 dark:text-white truncate">
            {server.displayName}
          </div>
          <div className="text-xs text-gray-500 dark:text-gray-400 truncate">{server.host}</div>
        </div>
        {isAdmin && (
          <div className="flex gap-1 flex-shrink-0">
            <button onClick={() => onEdit(server)} title="Edit"
              className="p-1 text-gray-400 hover:text-blue-500 transition-colors">
              <Pencil className="w-3.5 h-3.5" />
            </button>
            <button onClick={() => onDelete(server.id)} title="Delete"
              className="p-1 text-gray-400 hover:text-red-500 transition-colors">
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          </div>
        )}
      </div>

      {/* server details */}
      <div className="text-xs text-gray-500 dark:text-gray-400 space-y-0.5">
        <div>User: <span className="text-gray-700 dark:text-gray-300">{server.username}</span></div>
        <div className="truncate" title={server.scriptDir}>
          Scripts:&nbsp;
          <span className="text-gray-700 dark:text-gray-300 font-mono">{server.scriptDir}</span>
        </div>
        <div>SSH port: <span className="text-gray-700 dark:text-gray-300">{server.sshPort}</span></div>
      </div>

      {/* status row */}
      <div className="flex items-center justify-between">
        {isChecking ? (
          <span className="text-xs text-blue-500 flex items-center gap-1">
            <RefreshCw className="w-3.5 h-3.5 animate-spin" /> Checking…
          </span>
        ) : (
          <StatusBadge status={status?.status} />
        )}
        {status?.checkedAt && (
          <span className="text-xs text-gray-400">
            {new Date(status.checkedAt).toLocaleTimeString()}
          </span>
        )}
      </div>

      {/* error detail */}
      {status?.details && status.status === 'ERROR' && (
        <div className="text-xs text-orange-600 dark:text-orange-400 bg-orange-50 dark:bg-orange-900/20 rounded p-2 break-all">
          {status.details}
        </div>
      )}

      {/* action buttons */}
      <div className="flex flex-wrap gap-2 pt-1">
        <button onClick={() => onCheck(server.id)} disabled={isChecking || isPending}
          className="btn-secondary text-xs py-1 px-2 flex items-center gap-1">
          <RefreshCw className={`w-3 h-3 ${isChecking ? 'animate-spin' : ''}`} />
          Status
        </button>
        {isAdmin && (
          <>
            <button onClick={() => onStop(server.id)} disabled={isChecking || isPending}
              className="text-xs py-1 px-2 flex items-center gap-1 rounded border
                border-red-300 dark:border-red-700 text-red-600 dark:text-red-400
                hover:bg-red-50 dark:hover:bg-red-900/20 disabled:opacity-50 transition-colors">
              {isPending ? <RefreshCw className="w-3 h-3 animate-spin" /> : <Square className="w-3 h-3" />}
              Stop
            </button>
            <button onClick={() => onStart(server.id)} disabled={isChecking || isPending}
              className="text-xs py-1 px-2 flex items-center gap-1 rounded border
                border-green-300 dark:border-green-700 text-green-700 dark:text-green-400
                hover:bg-green-50 dark:hover:bg-green-900/20 disabled:opacity-50 transition-colors">
              {isPending ? <RefreshCw className="w-3 h-3 animate-spin" /> : <Play className="w-3 h-3" />}
              Start
            </button>
          </>
        )}
      </div>
    </div>
  );
}

// ── Server group (CORE_CACHE or DB_SERVER) ────────────────────────────────────

function ServerGroup({
  type, servers, ...cardProps
}: {
  type: CoherenceServerType;
  servers: CoherenceServerResponse[];
} & Omit<React.ComponentProps<typeof ServerCard>, 'server'>) {
  if (servers.length === 0) return null;
  return (
    <div className="space-y-3">
      <h4 className="text-sm font-semibold text-gray-600 dark:text-gray-400 flex items-center gap-2">
        <span className="w-2 h-2 rounded-full bg-blue-400 inline-block" />
        {SERVER_TYPE_LABELS[type]}
        <span className="text-xs font-normal text-gray-400">({servers.length})</span>
      </h4>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
        {servers.map(s => <ServerCard key={s.id} server={s} {...cardProps} />)}
      </div>
    </div>
  );
}

// ── Add / Edit modal ──────────────────────────────────────────────────────────

function ServerModal({
  initial, onClose, onSave,
}: {
  initial: CoherenceServerResponse | null;
  onClose: () => void;
  onSave: (req: CoherenceServerRequest, id?: number) => void;
}) {
  const isEdit = !!initial;
  const [form, setForm] = useState<CoherenceServerRequest>(
    initial
      ? {
          displayName:    initial.displayName,
          host:           initial.host,
          sshPort:        initial.sshPort,
          username:       initial.username,
          password:       '',
          environment:    initial.environment,
          serverType:     initial.serverType,
          scriptBasePath: initial.scriptBasePath,
          scriptInstance: initial.scriptInstance,
        }
      : { ...defaultForm }
  );
  const [testResult, setTestResult] = useState<{ ok: boolean; msg: string } | null>(null);
  const [testing, setTesting] = useState(false);

  const set = (k: keyof CoherenceServerRequest, v: string | number) =>
    setForm(f => ({ ...f, [k]: v }));

  const handleTest = async () => {
    setTesting(true); setTestResult(null);
    try {
      const res = await mqApi.coherence.testSsh(form);
      setTestResult({ ok: res.data.success, msg: res.data.message ?? res.data.data ?? '' });
    } catch (e: any) {
      setTestResult({ ok: false, msg: e.response?.data?.message ?? 'Test failed' });
    } finally { setTesting(false); }
  };

  // Computed preview of the script directory
  const scriptDirPreview = `${(form.scriptBasePath || DEFAULT_SCRIPT_BASE).replace(/\/+$/, '')}/${form.scriptInstance || DEFAULT_SCRIPT_INSTANCE}/bin`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl w-full max-w-lg">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200 dark:border-gray-700">
          <h3 className="font-semibold text-gray-900 dark:text-white">
            {isEdit ? 'Edit Coherence Server' : 'Add Coherence Server'}
          </h3>
          <button onClick={onClose}><X className="w-5 h-5 text-gray-400 hover:text-gray-600" /></button>
        </div>

        <div className="p-5 space-y-4 max-h-[75vh] overflow-y-auto">
          {/* Basic info */}
          <div className="grid grid-cols-2 gap-4">
            <div className="col-span-2">
              <label className="label">Display Name</label>
              <input className="input-field" value={form.displayName}
                onChange={e => set('displayName', e.target.value)}
                placeholder="e.g., DEV Cache Node 01" />
            </div>
            <div>
              <label className="label">Unix Host / Server Name</label>
              <input className="input-field" value={form.host}
                onChange={e => set('host', e.target.value)}
                placeholder="e.g., cache01.dev.corp" />
            </div>
            <div>
              <label className="label">SSH Port</label>
              <input className="input-field" type="number" value={form.sshPort ?? 22}
                onChange={e => set('sshPort', Number(e.target.value))} />
            </div>
            <div>
              <label className="label">Unix Username</label>
              <input className="input-field" value={form.username}
                onChange={e => set('username', e.target.value)}
                placeholder="syscouat" />
            </div>
            <div>
              <label className="label">
                Password {isEdit && <span className="text-gray-400 font-normal">(blank = keep)</span>}
              </label>
              <input className="input-field" type="password" value={form.password ?? ''}
                onChange={e => set('password', e.target.value)} />
            </div>
            <div>
              <label className="label">Environment</label>
              <select className="input-field" value={form.environment}
                onChange={e => set('environment', e.target.value as CoherenceEnvironment)}>
                {ENVIRONMENTS.map(e => <option key={e} value={e}>{e}</option>)}
              </select>
            </div>
            <div>
              <label className="label">Server Type</label>
              <select className="input-field" value={form.serverType}
                onChange={e => set('serverType', e.target.value as CoherenceServerType)}>
                <option value="CORE_CACHE">Core Cache Server</option>
                <option value="DB_SERVER">DB Server</option>
              </select>
            </div>
          </div>

          {/* Script path config */}
          <div className="border border-gray-200 dark:border-gray-600 rounded p-3 space-y-3">
            <p className="text-xs font-semibold text-gray-600 dark:text-gray-400">Script Path Configuration</p>
            <div>
              <label className="label">App Base Path</label>
              <input className="input-field font-mono text-sm" value={form.scriptBasePath ?? DEFAULT_SCRIPT_BASE}
                onChange={e => set('scriptBasePath', e.target.value)}
                placeholder={DEFAULT_SCRIPT_BASE} />
            </div>
            <div>
              <label className="label">Instance / Version</label>
              <input className="input-field font-mono text-sm" value={form.scriptInstance ?? DEFAULT_SCRIPT_INSTANCE}
                onChange={e => set('scriptInstance', e.target.value)}
                placeholder={DEFAULT_SCRIPT_INSTANCE} />
              <p className="text-xs text-gray-400 mt-1">
                Scripts directory:&nbsp;
                <span className="font-mono text-blue-600 dark:text-blue-400">{scriptDirPreview}</span>
              </p>
              <p className="text-xs text-gray-400 mt-0.5">
                Uses:&nbsp;
                <span className="font-mono">{scriptDirPreview}/stop.sh</span>&nbsp;·&nbsp;
                <span className="font-mono">start.sh</span>&nbsp;·&nbsp;
                <span className="font-mono">status.sh</span>
              </p>
            </div>
          </div>

          {testResult && (
            <div className={`p-3 rounded text-sm ${testResult.ok
              ? 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300'
              : 'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300'}`}>
              {testResult.msg}
            </div>
          )}
        </div>

        <div className="flex justify-between items-center px-5 py-4 border-t border-gray-200 dark:border-gray-700">
          <button onClick={handleTest} disabled={testing || !form.host || !form.username}
            className="btn-secondary flex items-center gap-2 text-sm">
            <TestTube className="w-4 h-4" />
            {testing ? 'Testing…' : 'Test SSH'}
          </button>
          <div className="flex gap-3">
            <button onClick={onClose} className="btn-secondary text-sm">Cancel</button>
            <button
              onClick={() => onSave(form, initial?.id)}
              disabled={!form.displayName || !form.host || !form.username}
              className="btn-primary text-sm">
              {isEdit ? 'Update' : 'Add Server'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function CoherencePage() {
  const isAdmin = useAuthStore(s => s.isAdmin);
  const queryClient = useQueryClient();

  const [activeEnv, setActiveEnv] = useState<CoherenceEnvironment>('DEV');
  const [modalServer, setModalServer] = useState<CoherenceServerResponse | null | 'new'>(null);
  const [statusMap, setStatusMap] = useState<Record<number, CoherenceStatusResponse>>({});
  const [checkingIds, setCheckingIds] = useState<Set<number>>(new Set());
  const [actionPending, setActionPending] = useState<number | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error'; msg: string } | null>(null);

  const showToast = (type: 'success' | 'error', msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 4000);
  };

  const { data: servers = [] } = useQuery<CoherenceServerResponse[]>({
    queryKey: ['coherence-servers'],
    queryFn: () => mqApi.coherence.listServers().then(r => r.data.data),
  });

  const saveMutation = useMutation({
    mutationFn: ({ req, id }: { req: CoherenceServerRequest; id?: number }) =>
      id ? mqApi.coherence.updateServer(id, req) : mqApi.coherence.createServer(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['coherence-servers'] });
      setModalServer(null);
      showToast('success', 'Server saved');
    },
    onError: (e: any) => showToast('error', e.response?.data?.message ?? 'Save failed'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => mqApi.coherence.deleteServer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['coherence-servers'] });
      showToast('success', 'Server removed');
    },
  });

  const checkStatus = async (id: number) => {
    setCheckingIds(s => new Set(s).add(id));
    try {
      const res = await mqApi.coherence.checkStatus(id);
      setStatusMap(m => ({ ...m, [id]: res.data.data }));
    } catch {
      setStatusMap(m => ({
        ...m,
        [id]: { serverId: id, host: '', status: 'ERROR',
          details: 'Request failed', checkedAt: new Date().toISOString() },
      }));
    } finally {
      setCheckingIds(s => { const n = new Set(s); n.delete(id); return n; });
    }
  };

  const checkAllInEnv = async () => {
    const envServers = servers.filter(s => s.environment === activeEnv);
    await Promise.all(envServers.map(s => checkStatus(s.id)));
  };

  const handleStop = async (id: number) => {
    setActionPending(id);
    try {
      const res = await mqApi.coherence.stopService(id);
      setStatusMap(m => ({ ...m, [id]: res.data.data }));
      showToast(res.data.success ? 'success' : 'error', res.data.message ?? 'Done');
    } catch (e: any) {
      showToast('error', e.response?.data?.message ?? 'Stop failed');
    } finally { setActionPending(null); }
  };

  const handleStart = async (id: number) => {
    setActionPending(id);
    try {
      const res = await mqApi.coherence.startService(id);
      setStatusMap(m => ({ ...m, [id]: res.data.data }));
      showToast(res.data.success ? 'success' : 'error', res.data.message ?? 'Done');
    } catch (e: any) {
      showToast('error', e.response?.data?.message ?? 'Start failed');
    } finally { setActionPending(null); }
  };

  const handleDelete = (id: number) => {
    if (window.confirm('Remove this server?')) deleteMutation.mutate(id);
  };

  const envServers = servers.filter(s => s.environment === activeEnv);
  const cardProps = {
    statusMap, checkingIds, actionPending,
    onCheck: checkStatus, onStop: handleStop, onStart: handleStart,
    onEdit: (s: CoherenceServerResponse) => setModalServer(s),
    onDelete: handleDelete, isAdmin: isAdmin(),
  };

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold text-gray-900 dark:text-white flex items-center gap-2">
            <ServerCog className="w-6 h-6 text-blue-500" />
            Coherence Servers
          </h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Monitor and control Oracle Coherence services across environments
          </p>
        </div>
        <div className="flex gap-2">
          <button onClick={checkAllInEnv} className="btn-secondary flex items-center gap-2 text-sm">
            <RefreshCw className="w-4 h-4" />
            Refresh {activeEnv}
          </button>
          {isAdmin() && (
            <button onClick={() => setModalServer('new')} className="btn-primary flex items-center gap-2 text-sm">
              <Plus className="w-4 h-4" /> Add Server
            </button>
          )}
        </div>
      </div>

      {/* Environment tabs */}
      <div className="border-b border-gray-200 dark:border-gray-700">
        <nav className="flex gap-1">
          {ENVIRONMENTS.map(env => {
            const count = servers.filter(s => s.environment === env).length;
            return (
              <button key={env} onClick={() => setActiveEnv(env)}
                className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
                  activeEnv === env
                    ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                    : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                }`}>
                {env}
                {count > 0 && (
                  <span className="ml-1.5 text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 rounded-full px-1.5 py-0.5">
                    {count}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
      </div>

      {/* Server groups */}
      {envServers.length === 0 ? (
        <div className="card p-10 text-center text-gray-500 dark:text-gray-400">
          <ServerCog className="w-10 h-10 mx-auto mb-3 opacity-30" />
          <p className="text-sm">No servers configured for {activeEnv}.</p>
          {isAdmin() && (
            <button onClick={() => setModalServer('new')}
              className="mt-3 text-sm text-blue-600 dark:text-blue-400 hover:underline">
              Add a server
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-6">
          {SERVER_TYPES.map(type => (
            <ServerGroup
              key={type}
              type={type}
              servers={envServers.filter(s => s.serverType === type)}
              {...cardProps}
            />
          ))}
        </div>
      )}

      {/* Toast */}
      {toast && (
        <div className={`fixed bottom-4 right-4 z-50 px-4 py-3 rounded shadow-lg text-sm text-white ${
          toast.type === 'success' ? 'bg-green-600' : 'bg-red-600'}`}>
          {toast.msg}
        </div>
      )}

      {/* Add/Edit modal */}
      {modalServer !== null && (
        <ServerModal
          initial={modalServer === 'new' ? null : modalServer}
          onClose={() => setModalServer(null)}
          onSave={(req, id) => saveMutation.mutate({ req, id })}
        />
      )}
    </div>
  );
}
