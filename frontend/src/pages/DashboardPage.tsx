import { useQuery } from '@tanstack/react-query';
import { Activity, Database, Clock, User } from 'lucide-react';
import { mqApi } from '../api/mqApi';
import { useConnectionStore } from '../store/connectionStore';
import type { AuditLogEntry } from '../types';

export default function DashboardPage() {
  const activeConfig = useConnectionStore(s => s.activeConfig);

  const { data: health } = useQuery({
    queryKey: ['health'],
    queryFn: () => mqApi.health().then(r => r.data.data),
    refetchInterval: 30_000,
  });

  const { data: auditLogs } = useQuery({
    queryKey: ['audit'],
    queryFn: () => mqApi.getAuditLog().then(r => r.data.data),
    refetchInterval: 60_000,
  });

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-semibold text-gray-900 dark:text-white">Dashboard</h2>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">IBM MQ Explorer overview</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="card p-5">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-medium text-gray-500 dark:text-gray-400">Status</span>
            <Activity className="w-4 h-4 text-green-500" />
          </div>
          <div className="text-2xl font-bold text-green-600 dark:text-green-400">
            {health?.status ?? 'UP'}
          </div>
          <div className="text-xs text-gray-400 mt-1">Backend service</div>
        </div>

        <div className="card p-5">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-medium text-gray-500 dark:text-gray-400">Active Connections</span>
            <Database className="w-4 h-4 text-blue-500" />
          </div>
          <div className="text-2xl font-bold text-gray-900 dark:text-white">
            {health?.activeConnections ?? 0}
          </div>
          <div className="text-xs text-gray-400 mt-1">MQ Queue Managers</div>
        </div>

        <div className="card p-5">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-medium text-gray-500 dark:text-gray-400">Current Config</span>
            <Database className="w-4 h-4 text-purple-500" />
          </div>
          <div className="text-lg font-bold text-gray-900 dark:text-white truncate">
            {activeConfig?.configName ?? '—'}
          </div>
          <div className="text-xs text-gray-400 mt-1">
            {activeConfig ? `${activeConfig.host}:${activeConfig.port}` : 'Not connected'}
          </div>
        </div>
      </div>

      {auditLogs && auditLogs.length > 0 && (
        <div className="card">
          <div className="px-5 py-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-200 flex items-center gap-2">
              <Clock className="w-4 h-4" />
              Recent Activity
            </h3>
          </div>
          <div className="divide-y divide-gray-100 dark:divide-gray-700">
            {auditLogs.slice(0, 15).map((log: AuditLogEntry) => (
              <div key={log.id} className="px-5 py-3 flex items-center gap-4 text-sm">
                <div className="flex items-center gap-2 w-28 flex-shrink-0">
                  <User className="w-3 h-3 text-gray-400" />
                  <span className="text-gray-600 dark:text-gray-300 font-medium truncate">{log.username}</span>
                </div>
                <span className={`px-2 py-0.5 rounded text-xs font-medium flex-shrink-0 ${
                  log.outcome === 'SUCCESS'
                    ? 'bg-green-100 dark:bg-green-900 text-green-700 dark:text-green-300'
                    : 'bg-red-100 dark:bg-red-900 text-red-700 dark:text-red-300'
                }`}>
                  {log.action}
                </span>
                <span className="text-gray-500 dark:text-gray-400 truncate flex-1">
                  {log.targetResource || log.queueManagerName || '—'}
                </span>
                <span className="text-gray-400 text-xs flex-shrink-0">
                  {new Date(log.timestamp).toLocaleTimeString()}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
