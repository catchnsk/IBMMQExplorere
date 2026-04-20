import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Panel, PanelGroup, PanelResizeHandle } from 'react-resizable-panels';
import { ArrowLeft, RefreshCw, Search, Filter } from 'lucide-react';
import { mqApi } from '../api/mqApi';
import { useConnectionStore } from '../store/connectionStore';
import MessageList from '../components/messages/MessageList';
import MessageViewer from '../components/messages/MessageViewer';
import type { MessageSummaryResponse } from '../types';

export default function MessageBrowserPage() {
  const { queueName } = useParams<{ queueName: string }>();
  const navigate = useNavigate();
  const { activeConfigId } = useConnectionStore();
  const [selectedMessage, setSelectedMessage] = useState<MessageSummaryResponse | null>(null);
  const [filterMsgId, setFilterMsgId] = useState('');
  const [filterCorrId, setFilterCorrId] = useState('');
  const [limit, setLimit] = useState(100);
  const [showFilters, setShowFilters] = useState(false);

  const { data: messages = [], isLoading, refetch, isFetching } = useQuery<MessageSummaryResponse[]>({
    queryKey: ['messages', activeConfigId, queueName, filterMsgId, filterCorrId, limit],
    queryFn: () => mqApi.browseMessages(activeConfigId!, queueName!, {
      messageId: filterMsgId || undefined,
      correlationId: filterCorrId || undefined,
      limit,
    }).then(r => r.data.data),
    enabled: !!activeConfigId && !!queueName,
  });

  const { data: messageDetail, isLoading: isLoadingDetail } = useQuery({
    queryKey: ['message-detail', activeConfigId, queueName, selectedMessage?.messageId],
    queryFn: () => mqApi.getMessage(activeConfigId!, queueName!, selectedMessage!.messageId)
      .then(r => r.data.data),
    enabled: !!selectedMessage && !!activeConfigId,
  });

  return (
    <div className="h-full flex flex-col space-y-4" style={{ height: 'calc(100vh - 160px)' }}>
      <div className="flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <button onClick={() => navigate('/queues')}
            className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300">
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h2 className="text-xl font-semibold text-gray-900 dark:text-white font-mono">
              {queueName}
            </h2>
            <p className="text-xs text-gray-500 dark:text-gray-400">
              {messages.length} message{messages.length !== 1 ? 's' : ''} (browse mode — non-destructive)
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setShowFilters(!showFilters)}
            className="btn-secondary flex items-center gap-2 text-sm">
            <Filter className="w-4 h-4" />
            Filters
          </button>
          <button onClick={() => refetch()} disabled={isFetching}
            className="btn-secondary flex items-center gap-2 text-sm">
            <RefreshCw className={`w-4 h-4 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {showFilters && (
        <div className="card p-4 flex-shrink-0">
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="label text-xs">Message ID (hex)</label>
              <input className="input-field text-xs font-mono"
                value={filterMsgId} onChange={e => setFilterMsgId(e.target.value)}
                placeholder="Filter by Message ID" />
            </div>
            <div>
              <label className="label text-xs">Correlation ID (hex)</label>
              <input className="input-field text-xs font-mono"
                value={filterCorrId} onChange={e => setFilterCorrId(e.target.value)}
                placeholder="Filter by Correlation ID" />
            </div>
            <div>
              <label className="label text-xs">Browse limit (max 500)</label>
              <input type="number" className="input-field text-xs"
                value={limit} min={1} max={500}
                onChange={e => setLimit(Math.min(500, Math.max(1, Number(e.target.value))))} />
            </div>
          </div>
        </div>
      )}

      <div className="flex-1 overflow-hidden">
        <PanelGroup direction="horizontal" className="h-full">
          <Panel defaultSize={40} minSize={25}>
            <MessageList
              messages={messages}
              isLoading={isLoading}
              selected={selectedMessage}
              onSelect={setSelectedMessage}
            />
          </Panel>
          <PanelResizeHandle className="w-1.5 bg-gray-200 dark:bg-gray-700 hover:bg-blue-400 transition-colors cursor-col-resize" />
          <Panel defaultSize={60} minSize={30}>
            <MessageViewer
              message={messageDetail ?? null}
              isLoading={isLoadingDetail}
              queueName={queueName!}
            />
          </Panel>
        </PanelGroup>
      </div>
    </div>
  );
}
