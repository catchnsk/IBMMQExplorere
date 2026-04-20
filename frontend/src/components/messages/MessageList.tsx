import { RefreshCw, MessageSquare } from 'lucide-react';
import type { MessageSummaryResponse } from '../../types';
import clsx from 'clsx';

interface Props {
  messages: MessageSummaryResponse[];
  isLoading: boolean;
  selected: MessageSummaryResponse | null;
  onSelect: (msg: MessageSummaryResponse) => void;
}

export default function MessageList({ messages, isLoading, selected, onSelect }: Props) {
  if (isLoading) {
    return (
      <div className="h-full flex items-center justify-center text-gray-500 dark:text-gray-400">
        <RefreshCw className="w-5 h-5 animate-spin mr-2" />
        Loading messages...
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <div className="h-full flex flex-col items-center justify-center text-gray-400">
        <MessageSquare className="w-10 h-10 mb-2 opacity-30" />
        <p className="text-sm">No messages in queue</p>
      </div>
    );
  }

  return (
    <div className="h-full overflow-auto border border-gray-200 dark:border-gray-700 rounded-lg">
      <div className="sticky top-0 bg-gray-50 dark:bg-gray-700 px-3 py-2 border-b border-gray-200 dark:border-gray-600">
        <span className="text-xs font-medium text-gray-500 dark:text-gray-400">
          {messages.length} message{messages.length !== 1 ? 's' : ''}
        </span>
      </div>
      {messages.map((msg) => (
        <div
          key={msg.messageId}
          onClick={() => onSelect(msg)}
          className={clsx(
            'px-3 py-3 cursor-pointer border-b border-gray-100 dark:border-gray-700 hover:bg-blue-50 dark:hover:bg-blue-900/10 transition-colors',
            selected?.messageId === msg.messageId && 'bg-blue-50 dark:bg-blue-900/20 border-l-2 border-l-blue-500'
          )}
        >
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs font-medium text-gray-500 dark:text-gray-400">#{msg.index + 1}</span>
            <div className="flex items-center gap-2">
              {msg.persistence === 1 && (
                <span className="text-xs bg-yellow-100 dark:bg-yellow-900/40 text-yellow-700 dark:text-yellow-400 px-1 rounded">PERSIST</span>
              )}
              <span className="text-xs text-gray-400">P{msg.priority}</span>
            </div>
          </div>
          <div className="font-mono text-xs text-gray-700 dark:text-gray-300 truncate mb-1">
            {msg.messageId.substring(0, 32)}...
          </div>
          <div className="flex items-center justify-between text-xs text-gray-400">
            <span>{msg.format || '—'}</span>
            <span>{msg.dataLength.toLocaleString()} bytes</span>
            {msg.putTimestamp && (
              <span>{new Date(msg.putTimestamp).toLocaleString()}</span>
            )}
          </div>
          {msg.putApplicationName && (
            <div className="text-xs text-gray-400 truncate mt-1">{msg.putApplicationName}</div>
          )}
        </div>
      ))}
    </div>
  );
}
