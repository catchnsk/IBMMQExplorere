import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { RefreshCw, Search, Database, AlertCircle } from 'lucide-react';
import { mqApi } from '../api/mqApi';
import { useConnectionStore } from '../store/connectionStore';
import type { QueueInfoResponse } from '../types';

export default function QueuesPage() {
  const { activeConfigId } = useConnectionStore();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [includeSystem, setIncludeSystem] = useState(false);

  const { data: queues = [], isLoading, error, refetch, isFetching } = useQuery<QueueInfoResponse[]>({
    queryKey: ['queues', activeConfigId, includeSystem],
    queryFn: () => mqApi.listQueues(activeConfigId!, includeSystem).then(r => r.data.data),
    enabled: !!activeConfigId,
  });

  const filtered = queues.filter(q =>
    q.name.toLowerCase().includes(search.toLowerCase()) ||
    (q.description?.toLowerCase().includes(search.toLowerCase()) ?? false)
  );

  if (!activeConfigId) {
    return (
      <div className="flex flex-col items-center justify-center h-64 text-gray-500 dark:text-gray-400">
        <Database className="w-12 h-12 mb-3 opacity-30" />
        <p className="text-lg font-medium">Not Connected</p>
        <p className="text-sm">Go to Configuration and connect to a queue manager first.</p>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold text-gray-900 dark:text-white">Queues</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            {queues.length} queue{queues.length !== 1 ? 's' : ''} found
          </p>
        </div>
        <button onClick={() => refetch()} disabled={isFetching}
          className="btn-secondary flex items-center gap-2 text-sm">
          <RefreshCw className={`w-4 h-4 ${isFetching ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      <div className="flex items-center gap-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            type="text"
            placeholder="Search queues..."
            className="input-field pl-9"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-300 cursor-pointer">
          <input type="checkbox" checked={includeSystem}
            onChange={e => setIncludeSystem(e.target.checked)} />
          Include system queues
        </label>
      </div>

      {error && (
        <div className="flex items-center gap-2 p-4 bg-red-50 dark:bg-red-900/20 border border-red-300 dark:border-red-700 rounded text-red-700 dark:text-red-300 text-sm">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{(error as any).response?.data?.message ?? 'Failed to load queues'}</span>
        </div>
      )}

      {isLoading ? (
        <div className="text-center py-12 text-gray-500 dark:text-gray-400">
          <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2" />
          Loading queues...
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr>
                  {['Queue Name', 'Type', 'Depth', 'Max', 'Consumers', 'Producers', 'Get', 'Put', 'Description'].map(h => (
                    <th key={h} className="table-header text-left whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="table-cell text-center text-gray-400 py-8">
                      {search ? 'No queues match your search.' : 'No queues available.'}
                    </td>
                  </tr>
                ) : filtered.map(queue => (
                  <tr
                    key={queue.name}
                    className="hover:bg-blue-50 dark:hover:bg-blue-900/10 cursor-pointer transition-colors"
                    onClick={() => navigate(`/ibm-mq/queues/${encodeURIComponent(queue.name)}`)}
                  >
                    <td className="table-cell font-mono text-blue-600 dark:text-blue-400 font-medium">
                      {queue.name}
                    </td>
                    <td className="table-cell">
                      <span className="px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 rounded text-xs">
                        {queue.type}
                      </span>
                    </td>
                    <td className="table-cell">
                      <span className={`font-medium ${queue.currentDepth > 0 ? 'text-amber-600 dark:text-amber-400' : ''}`}>
                        {queue.currentDepth.toLocaleString()}
                      </span>
                    </td>
                    <td className="table-cell text-gray-500">{queue.maxDepth?.toLocaleString()}</td>
                    <td className="table-cell">{queue.openInputCount}</td>
                    <td className="table-cell">{queue.openOutputCount}</td>
                    <td className="table-cell">
                      {queue.getInhibited
                        ? <span className="text-red-500 text-xs">INHIBITED</span>
                        : <span className="text-green-500 text-xs">OK</span>}
                    </td>
                    <td className="table-cell">
                      {queue.putInhibited
                        ? <span className="text-red-500 text-xs">INHIBITED</span>
                        : <span className="text-green-500 text-xs">OK</span>}
                    </td>
                    <td className="table-cell text-gray-500 max-w-xs truncate">
                      {queue.description || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
