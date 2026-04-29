import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Plus, Pencil, Trash2, List, RefreshCw, X, ChevronDown, ChevronRight,
  Server, AlertCircle, CheckCircle2, Loader2, TestTube, Lock,
  Play, Square, Activity,
} from 'lucide-react';
import { mqApi } from '../api/mqApi';
import { useAuthStore } from '../store/authStore';
import type {
  AmqServerRequest, AmqServerResponse, AmqQueueInfo,
  AmqEnvironment, AmqGroupCategory, AmqBrokerType, AmqStatusResponse, AmqServiceStatus,
} from '../types';

const ENVIRONMENTS: AmqEnvironment[] = ['QA', 'QA03', 'PERF'];
const GROUP_LABELS: Record<AmqGroupCategory, string> = { GROUP_A: 'Group - A', GROUP_B: 'Group - B' };
const GROUPS: AmqGroupCategory[] = ['GROUP_A', 'GROUP_B'];

const defaultForm: AmqServerRequest = {
  displayName: '', host: '', managementPort: 8161,
  username: '', password: '',
  sshPort: 22, sshUsername: '', sshPassword: '',
  instanceUser: '', instanceName: '',
  environment: 'QA', groupCategory: 'GROUP_A', brokerType: 'CLASSIC', useSsl: false,
};

export default function AmqPage() {
  const isAdmin = useAuthStore(s => s.isAdmin());
  const queryClient = useQueryClient();

  const [activeEnv, setActiveEnv] = useState<AmqEnvironment>('QA');
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set(['QA-GROUP_A', 'QA-GROUP_B']));
  const [expandedQueues, setExpandedQueues] = useState<Set<number>>(new Set());
  const [showModal, setShowModal] = useState(false);
  const [editServer, setEditServer] = useState<AmqServerResponse | null>(null);
  const [form, setForm] = useState<AmqServerRequest>(defaultForm);
  const [testResult, setTestResult] = useState<{ ok: boolean; msg: string } | null>(null);
  const [isTesting, setIsTesting] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);

  const { data: allServers = [], isLoading } = useQuery<AmqServerResponse[]>({
    queryKey: ['amq-servers'],
    queryFn: () => mqApi.amq.listServers().then(r => r.data.data),
  });

  const serversForEnv = allServers.filter(s => s.environment === activeEnv);
  const groupedServers = (group: AmqGroupCategory) => serversForEnv.filter(s => s.groupCategory === group);

  const createMut = useMutation({
    mutationFn: (data: AmqServerRequest) => mqApi.amq.createServer(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['amq-servers'] }); closeModal(); },
  });
  const updateMut = useMutation({
    mutationFn: ({ id, data }: { id: number; data: AmqServerRequest }) => mqApi.amq.updateServer(id, data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['amq-servers'] }); closeModal(); },
  });
  const deleteMut = useMutation({
    mutationFn: (id: number) => mqApi.amq.deleteServer(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['amq-servers'] }); setDeleteConfirm(null); },
  });

  const openCreate = () => {
    setEditServer(null);
    setForm({ ...defaultForm, environment: activeEnv });
    setTestResult(null);
    setShowModal(true);
  };
  const openEdit = (s: AmqServerResponse) => {
    setEditServer(s);
    setForm({
      displayName: s.displayName, host: s.host, managementPort: s.managementPort,
      username: s.username ?? '', password: '',
      sshPort: s.sshPort, sshUsername: s.sshUsername ?? '', sshPassword: '',
      instanceUser: s.instanceUser ?? '', instanceName: s.instanceName ?? '',
      environment: s.environment, groupCategory: s.groupCategory,
      brokerType: s.brokerType, useSsl: s.useSsl,
    });
    setTestResult(null);
    setShowModal(true);
  };
  const closeModal = () => { setShowModal(false); setEditServer(null); setTestResult(null); };

  const handleSave = () => {
    if (editServer) updateMut.mutate({ id: editServer.id, data: form });
    else createMut.mutate(form);
  };

  const handleTest = async () => {
    setIsTesting(true); setTestResult(null);
    try {
      const r = await mqApi.amq.testConnection(form);
      setTestResult({ ok: r.data.success, msg: r.data.message ?? r.data.data ?? '' });
    } catch { setTestResult({ ok: false, msg: 'Request failed' }); }
    finally { setIsTesting(false); }
  };

  const handleTestSsh = async () => {
    setIsTesting(true); setTestResult(null);
    try {
      const r = await mqApi.amq.testSsh(form);
      setTestResult({ ok: r.data.success, msg: r.data.message ?? r.data.data ?? '' });
    } catch { setTestResult({ ok: false, msg: 'SSH request failed' }); }
    finally { setIsTesting(false); }
  };

  const toggleGroup = (key: string) => setExpandedGroups(prev => {
    const next = new Set(prev); next.has(key) ? next.delete(key) : next.add(key); return next;
  });
  const toggleQueues = (id: number) => setExpandedQueues(prev => {
    const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next;
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold text-gray-900 dark:text-white">AMQ Servers</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            ActiveMQ broker management — service control &amp; queue monitoring
          </p>
        </div>
        {isAdmin && (
          <button onClick={openCreate} className="btn-primary flex items-center gap-2">
            <Plus className="w-4 h-4" /> Add Server
          </button>
        )}
      </div>

      {/* Environment tabs */}
      <div className="border-b border-gray-200 dark:border-gray-700">
        <nav className="flex gap-1">
          {ENVIRONMENTS.map(env => (
            <button key={env}
              onClick={() => { setActiveEnv(env); setExpandedGroups(new Set([`${env}-GROUP_A`, `${env}-GROUP_B`])); }}
              className={`px-5 py-2 text-sm font-medium border-b-2 transition-colors ${
                activeEnv === env
                  ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                  : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'
              }`}>
              {env}
            </button>
          ))}
        </nav>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-12"><Loader2 className="w-6 h-6 animate-spin text-blue-500" /></div>
      ) : (
        <div className="space-y-4">
          {GROUPS.map(group => {
            const servers = groupedServers(group);
            const groupKey = `${activeEnv}-${group}`;
            const expanded = expandedGroups.has(groupKey);
            return (
              <div key={group} className="card overflow-hidden">
                <button onClick={() => toggleGroup(groupKey)}
                  className="w-full flex items-center justify-between px-4 py-3 bg-gray-50 dark:bg-gray-800/50 hover:bg-gray-100 dark:hover:bg-gray-700/50 transition-colors">
                  <div className="flex items-center gap-3">
                    {expanded ? <ChevronDown className="w-4 h-4 text-gray-500" /> : <ChevronRight className="w-4 h-4 text-gray-500" />}
                    <span className="font-semibold text-gray-800 dark:text-gray-100">{GROUP_LABELS[group]}</span>
                    <span className="text-xs bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 px-2 py-0.5 rounded-full">
                      {servers.length} server{servers.length !== 1 ? 's' : ''}
                    </span>
                  </div>
                </button>

                {expanded && (
                  <div className="divide-y divide-gray-100 dark:divide-gray-700">
                    {servers.length === 0 ? (
                      <p className="px-6 py-6 text-sm text-gray-400 italic">No servers configured for this group.</p>
                    ) : (
                      servers.map(server => (
                        <ServerCard key={server.id} server={server} isAdmin={isAdmin}
                          queueExpanded={expandedQueues.has(server.id)}
                          onToggleQueues={() => toggleQueues(server.id)}
                          onEdit={() => openEdit(server)}
                          onDelete={() => setDeleteConfirm(server.id)} />
                      ))
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {showModal && (
        <ServerModal form={form} setForm={setForm} isEdit={!!editServer}
          isSaving={createMut.isPending || updateMut.isPending}
          isTesting={isTesting} testResult={testResult}
          onSave={handleSave} onTest={handleTest} onTestSsh={handleTestSsh} onClose={closeModal} />
      )}

      {deleteConfirm !== null && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="card p-6 w-80 space-y-4">
            <p className="text-sm text-gray-700 dark:text-gray-300">Remove this AMQ server?</p>
            <div className="flex justify-end gap-2">
              <button onClick={() => setDeleteConfirm(null)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={() => deleteMut.mutate(deleteConfirm!)}
                className="bg-red-600 hover:bg-red-700 text-white px-4 py-2 rounded text-sm"
                disabled={deleteMut.isPending}>
                {deleteMut.isPending ? 'Removing…' : 'Remove'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Status badge ──────────────────────────────────────────────────────────────

function StatusBadge({ status }: { status?: AmqServiceStatus }) {
  if (!status) return null;
  const cfg: Record<AmqServiceStatus, { cls: string; label: string }> = {
    RUNNING: { cls: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300', label: 'Running' },
    STOPPED: { cls: 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300', label: 'Stopped' },
    UNKNOWN: { cls: 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300', label: 'Unknown' },
    ERROR:   { cls: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300', label: 'Error' },
  };
  const { cls, label } = cfg[status];
  return <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${cls}`}>{label}</span>;
}

// ── Server Card ───────────────────────────────────────────────────────────────

function ServerCard({ server, isAdmin, queueExpanded, onToggleQueues, onEdit, onDelete }: {
  server: AmqServerResponse;
  isAdmin: boolean;
  queueExpanded: boolean;
  onToggleQueues: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const queryClient = useQueryClient();
  const [statusDetails, setStatusDetails] = useState<string | null>(null);

  const statusKey = ['amq-status', server.id];

  const { data: statusData, isFetching: statusFetching } = useQuery<AmqStatusResponse>({
    queryKey: statusKey,
    queryFn: () => mqApi.amq.checkStatus(server.id).then(r => r.data.data),
    enabled: false,
    staleTime: Infinity,
  });

  const stopMut = useMutation({
    mutationFn: () => mqApi.amq.stopService(server.id),
    onSuccess: r => {
      queryClient.setQueryData(statusKey, r.data.data);
      setStatusDetails(r.data.data.details ?? null);
    },
  });

  const startMut = useMutation({
    mutationFn: () => mqApi.amq.startService(server.id),
    onSuccess: r => {
      queryClient.setQueryData(statusKey, r.data.data);
      setStatusDetails(r.data.data.details ?? null);
    },
  });

  const handleStatus = () => {
    setStatusDetails(null);
    queryClient.fetchQuery({
      queryKey: statusKey,
      queryFn: () => mqApi.amq.checkStatus(server.id).then(r => {
        setStatusDetails(r.data.data.details ?? null);
        return r.data.data;
      }),
    });
  };

  const isBusy = statusFetching || stopMut.isPending || startMut.isPending;
  const scheme = server.useSsl ? 'https' : 'http';
  const consoleUrl = `${scheme}://${server.host}:${server.managementPort}`;

  const { data: queues, isLoading: queuesLoading, refetch: refetchQueues, isFetching: queuesFetching } =
    useQuery<AmqQueueInfo[]>({
      queryKey: ['amq-queues', server.id],
      queryFn: () => mqApi.amq.listQueues(server.id).then(r => {
        if (!r.data.success) throw new Error(r.data.message);
        return r.data.data;
      }),
      enabled: queueExpanded,
      staleTime: 30_000,
    });

  return (
    <div className="px-4 py-3 space-y-3">
      {/* Server info row */}
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3 min-w-0">
          <Server className="w-5 h-5 text-blue-400 mt-0.5 flex-shrink-0" />
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="font-medium text-gray-900 dark:text-white">{server.displayName}</span>
              <span className="text-xs px-1.5 py-0.5 rounded bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300">
                {server.brokerType}
              </span>
              {server.useSsl && (
                <span className="text-xs px-1.5 py-0.5 rounded bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300 flex items-center gap-1">
                  <Lock className="w-3 h-3" /> SSL
                </span>
              )}
              <StatusBadge status={statusData?.status} />
            </div>
            <div className="text-xs text-gray-500 dark:text-gray-400 mt-0.5 font-mono">
              {server.host}:{server.managementPort}
              {server.sshUsername && <span className="ml-2">SSH: {server.sshUsername}</span>}
            </div>
            {server.instanceName && (
              <div className="text-xs text-gray-400 dark:text-gray-500 font-mono mt-0.5">
                {server.binDir}
              </div>
            )}
          </div>
        </div>

        {/* Action buttons */}
        <div className="flex items-center gap-1.5 flex-shrink-0 flex-wrap justify-end">
          {/* Status */}
          <button onClick={handleStatus} disabled={isBusy} title="Check Status"
            className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition-colors">
            {statusFetching ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Activity className="w-3.5 h-3.5" />}
            Status
          </button>

          {/* Stop — admin only */}
          {isAdmin && (
            <button onClick={() => { setStatusDetails(null); stopMut.mutate(); }}
              disabled={isBusy} title="Stop Service"
              className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded border border-red-300 dark:border-red-700 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 disabled:opacity-50 transition-colors">
              {stopMut.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Square className="w-3.5 h-3.5" />}
              Stop
            </button>
          )}

          {/* Start — admin only */}
          {isAdmin && (
            <button onClick={() => { setStatusDetails(null); startMut.mutate(); }}
              disabled={isBusy} title="Start Service"
              className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded border border-green-300 dark:border-green-700 text-green-600 dark:text-green-400 hover:bg-green-50 dark:hover:bg-green-900/20 disabled:opacity-50 transition-colors">
              {startMut.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
              Start
            </button>
          )}

          {/* Queues toggle */}
          <button onClick={onToggleQueues}
            className="flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded border border-blue-300 dark:border-blue-700 text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors">
            {queueExpanded ? <ChevronDown className="w-3.5 h-3.5" /> : <List className="w-3.5 h-3.5" />}
            Queues
          </button>

          {isAdmin && (
            <>
              <button onClick={onEdit}
                className="p-1.5 text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition-colors rounded hover:bg-gray-100 dark:hover:bg-gray-700">
                <Pencil className="w-3.5 h-3.5" />
              </button>
              <button onClick={onDelete}
                className="p-1.5 text-gray-400 hover:text-red-600 dark:hover:text-red-400 transition-colors rounded hover:bg-gray-100 dark:hover:bg-gray-700">
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </>
          )}
        </div>
      </div>

      {/* Status output */}
      {statusDetails && (
        <pre className="ml-8 text-xs font-mono bg-gray-50 dark:bg-gray-800/60 border border-gray-200 dark:border-gray-700 rounded px-3 py-2 whitespace-pre-wrap break-all text-gray-700 dark:text-gray-300 max-h-32 overflow-auto">
          {statusDetails}
        </pre>
      )}

      {/* Queue panel */}
      {queueExpanded && (
        <div className="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden ml-8">
          <div className="flex items-center justify-between px-3 py-2 bg-gray-50 dark:bg-gray-800/60">
            <span className="text-xs font-medium text-gray-600 dark:text-gray-300">
              Queues{queues ? ` (${queues.length})` : ''}
            </span>
            <div className="flex items-center gap-2">
              <a href={consoleUrl} target="_blank" rel="noreferrer"
                className="text-xs text-blue-500 hover:underline">Open Console ↗</a>
              <button onClick={() => refetchQueues()} disabled={queuesFetching}
                className="p-1 text-gray-400 hover:text-blue-500 transition-colors">
                <RefreshCw className={`w-3.5 h-3.5 ${queuesFetching ? 'animate-spin' : ''}`} />
              </button>
            </div>
          </div>
          <QueueTable queues={queues} isLoading={queuesLoading} />
        </div>
      )}
    </div>
  );
}

// ── Queue Table ───────────────────────────────────────────────────────────────

function QueueTable({ queues, isLoading }: { queues?: AmqQueueInfo[]; isLoading: boolean }) {
  const [search, setSearch] = useState('');

  if (isLoading) return <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-blue-500" /></div>;
  if (!queues) return null;
  if (queues.length === 0) return <p className="px-4 py-4 text-xs text-gray-400 italic">No queues found on this broker.</p>;

  const filtered = search ? queues.filter(q => q.name.toLowerCase().includes(search.toLowerCase())) : queues;

  return (
    <div>
      <div className="px-3 py-2 border-b border-gray-200 dark:border-gray-700">
        <input className="w-full text-xs px-2 py-1.5 rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:outline-none focus:ring-1 focus:ring-blue-500"
          placeholder="Filter queues…" value={search} onChange={e => setSearch(e.target.value)} />
      </div>
      <div className="overflow-auto max-h-72">
        <table className="w-full text-xs">
          <thead className="sticky top-0 bg-gray-50 dark:bg-gray-800">
            <tr className="text-left text-gray-500 dark:text-gray-400">
              <th className="px-3 py-2 font-medium">Queue Name</th>
              <th className="px-3 py-2 font-medium text-right">Depth</th>
              <th className="px-3 py-2 font-medium text-right">Consumers</th>
              <th className="px-3 py-2 font-medium text-right">Producers</th>
              <th className="px-3 py-2 font-medium text-right">Enqueue</th>
              <th className="px-3 py-2 font-medium text-right">Dequeue</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
            {filtered.map(q => (
              <tr key={q.name} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                <td className="px-3 py-2 font-mono text-gray-900 dark:text-white">{q.name}</td>
                <td className={`px-3 py-2 text-right font-medium ${(q.queueSize ?? 0) > 0 ? 'text-amber-600 dark:text-amber-400' : 'text-gray-500 dark:text-gray-400'}`}>
                  {q.queueSize ?? '—'}
                </td>
                <td className="px-3 py-2 text-right text-gray-500 dark:text-gray-400">{q.consumerCount ?? '—'}</td>
                <td className="px-3 py-2 text-right text-gray-500 dark:text-gray-400">{q.producerCount ?? '—'}</td>
                <td className="px-3 py-2 text-right text-gray-500 dark:text-gray-400">{q.enqueueCount?.toLocaleString() ?? '—'}</td>
                <td className="px-3 py-2 text-right text-gray-500 dark:text-gray-400">{q.dequeueCount?.toLocaleString() ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && <p className="px-4 py-3 text-xs text-gray-400 italic">No queues match "{search}".</p>}
      </div>
    </div>
  );
}

// ── Server Modal ──────────────────────────────────────────────────────────────

function ServerModal({ form, setForm, isEdit, isSaving, isTesting, testResult, onSave, onTest, onTestSsh, onClose }: {
  form: AmqServerRequest;
  setForm: (f: AmqServerRequest) => void;
  isEdit: boolean;
  isSaving: boolean;
  isTesting: boolean;
  testResult: { ok: boolean; msg: string } | null;
  onSave: () => void;
  onTest: () => void;
  onTestSsh: () => void;
  onClose: () => void;
}) {
  const set = (k: keyof AmqServerRequest, v: unknown) => setForm({ ...form, [k]: v });
  const valid = !!(form.displayName.trim() && form.host.trim());

  const sshUser = form.sshUsername?.trim() || form.username?.trim() || '';
  const binPreview = (sshUser || form.instanceUser?.trim())
    ? `/apps/amq/instances/${form.instanceUser?.trim() || sshUser}/${form.instanceName?.trim() || 'amq'}/bin`
    : '';

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="card w-full max-w-xl max-h-[92vh] overflow-y-auto">
        <div className="flex items-center justify-between p-5 border-b border-gray-200 dark:border-gray-700">
          <h3 className="text-base font-semibold text-gray-900 dark:text-white">
            {isEdit ? 'Edit AMQ Server' : 'Add AMQ Server'}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-5 space-y-5">
          {/* Display name */}
          <div>
            <label className="label">Display Name <span className="text-red-500">*</span></label>
            <input className="input-field" value={form.displayName}
              onChange={e => set('displayName', e.target.value)} placeholder="e.g. QA AMQ Broker 01" />
          </div>

          {/* Environment + Group */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Environment <span className="text-red-500">*</span></label>
              <select className="input-field" value={form.environment}
                onChange={e => set('environment', e.target.value as AmqEnvironment)}>
                {(['QA', 'QA03', 'PERF'] as AmqEnvironment[]).map(e => <option key={e} value={e}>{e}</option>)}
              </select>
            </div>
            <div>
              <label className="label">Group <span className="text-red-500">*</span></label>
              <select className="input-field" value={form.groupCategory}
                onChange={e => set('groupCategory', e.target.value as AmqGroupCategory)}>
                <option value="GROUP_A">Group - A</option>
                <option value="GROUP_B">Group - B</option>
              </select>
            </div>
          </div>

          {/* ── Console section ── */}
          <div className="space-y-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Web Console (Jolokia)</p>
            <div className="grid grid-cols-3 gap-3">
              <div className="col-span-2">
                <label className="label">Host <span className="text-red-500">*</span></label>
                <input className="input-field font-mono" value={form.host}
                  onChange={e => set('host', e.target.value)} placeholder="hostname or IP" />
              </div>
              <div>
                <label className="label">Mgmt Port</label>
                <input type="number" className="input-field" value={form.managementPort ?? 8161}
                  onChange={e => set('managementPort', Number(e.target.value))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label">Broker Type</label>
                <select className="input-field" value={form.brokerType ?? 'CLASSIC'}
                  onChange={e => set('brokerType', e.target.value as AmqBrokerType)}>
                  <option value="CLASSIC">ActiveMQ Classic</option>
                  <option value="ARTEMIS">ActiveMQ Artemis</option>
                </select>
              </div>
              <div className="flex items-end pb-1">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input type="checkbox" className="w-4 h-4 rounded accent-blue-600"
                    checked={form.useSsl ?? false} onChange={e => set('useSsl', e.target.checked)} />
                  <span className="text-sm text-gray-700 dark:text-gray-300">Use HTTPS (SSL)</span>
                </label>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label">Console Username</label>
                <input className="input-field" value={form.username ?? ''}
                  onChange={e => set('username', e.target.value)} placeholder="admin" />
              </div>
              <div>
                <label className="label">Console Password {isEdit && <span className="text-xs text-gray-400">(blank = keep)</span>}</label>
                <input type="password" className="input-field" value={form.password ?? ''}
                  onChange={e => set('password', e.target.value)} placeholder="••••••••" />
              </div>
            </div>
            {form.host && (
              <p className="text-xs text-gray-400 font-mono bg-gray-50 dark:bg-gray-800 px-3 py-1.5 rounded">
                {form.useSsl ? 'https' : 'http'}://{form.host}:{form.managementPort ?? 8161}
                {form.brokerType === 'ARTEMIS' ? '/console/jolokia' : '/api/jolokia'}
              </p>
            )}
          </div>

          {/* ── SSH section ── */}
          <div className="space-y-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">SSH (for Start / Stop / Status)</p>
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="label">SSH Port</label>
                <input type="number" className="input-field" value={form.sshPort ?? 22}
                  onChange={e => set('sshPort', Number(e.target.value))} />
              </div>
              <div>
                <label className="label">SSH Username</label>
                <input className="input-field" value={form.sshUsername ?? ''}
                  onChange={e => set('sshUsername', e.target.value)} placeholder="same as console" />
              </div>
              <div>
                <label className="label">SSH Password {isEdit && <span className="text-xs text-gray-400">(blank = keep)</span>}</label>
                <input type="password" className="input-field" value={form.sshPassword ?? ''}
                  onChange={e => set('sshPassword', e.target.value)} placeholder="••••••••" />
              </div>
            </div>
          </div>

          {/* ── Script path section ── */}
          <div className="space-y-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Script Path</p>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label">Instance User</label>
                <input className="input-field font-mono" value={form.instanceUser ?? ''}
                  onChange={e => set('instanceUser', e.target.value)} placeholder="usrid" />
              </div>
              <div>
                <label className="label">Instance Name</label>
                <input className="input-field font-mono" value={form.instanceName ?? ''}
                  onChange={e => set('instanceName', e.target.value)} placeholder="amq_instance" />
              </div>
            </div>
            {binPreview && (
              <div className="text-xs font-mono bg-gray-50 dark:bg-gray-800 px-3 py-2 rounded space-y-1 text-gray-500 dark:text-gray-400">
                <div>Start: <span className="text-gray-700 dark:text-gray-200">sudo /opt/chef/script/chef-cw</span></div>
                <div>Stop:  <span className="text-gray-700 dark:text-gray-200">{binPreview}/artemis-service stop</span></div>
                <div>Status: <span className="text-gray-700 dark:text-gray-200">{binPreview}/artemis-service status</span></div>
              </div>
            )}
          </div>

          {testResult && (
            <div className={`flex items-start gap-2 text-sm p-3 rounded ${testResult.ok ? 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300' : 'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300'}`}>
              {testResult.ok ? <CheckCircle2 className="w-4 h-4 flex-shrink-0 mt-0.5" /> : <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />}
              <span>{testResult.msg}</span>
            </div>
          )}
        </div>

        <div className="flex items-center justify-between p-5 border-t border-gray-200 dark:border-gray-700 gap-2 flex-wrap">
          <div className="flex gap-2">
            <button onClick={onTest} disabled={!valid || isTesting}
              className="btn-secondary flex items-center gap-1.5 text-sm disabled:opacity-50">
              {isTesting ? <Loader2 className="w-4 h-4 animate-spin" /> : <TestTube className="w-4 h-4" />}
              Test Console
            </button>
            <button onClick={onTestSsh} disabled={!valid || isTesting}
              className="btn-secondary flex items-center gap-1.5 text-sm disabled:opacity-50">
              {isTesting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Activity className="w-4 h-4" />}
              Test SSH
            </button>
          </div>
          <div className="flex gap-2">
            <button onClick={onClose} className="btn-secondary text-sm">Cancel</button>
            <button onClick={onSave} disabled={!valid || isSaving}
              className="btn-primary text-sm disabled:opacity-50">
              {isSaving ? 'Saving…' : isEdit ? 'Update' : 'Add Server'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
