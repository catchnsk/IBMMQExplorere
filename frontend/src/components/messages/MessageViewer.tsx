import { useState } from 'react';
import { Copy, Download, ChevronDown, ChevronRight, RefreshCw } from 'lucide-react';
import SyntaxHighlighter from 'react-syntax-highlighter';
import { atomOneDark, atomOneLight } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import { useThemeStore } from '../../store/themeStore';
import type { MessageDetailResponse } from '../../types';

type Tab = 'text' | 'json' | 'xml' | 'hex';

interface Props {
  message: MessageDetailResponse | null;
  isLoading: boolean;
  queueName: string;
}

function MqmdRow({ label, value }: { label: string; value: string | number | undefined }) {
  if (value === undefined || value === null || value === '') return null;
  return (
    <tr>
      <td className="py-1 pr-4 text-xs font-medium text-gray-500 dark:text-gray-400 whitespace-nowrap">{label}</td>
      <td className="py-1 text-xs font-mono text-gray-800 dark:text-gray-200 break-all">{String(value)}</td>
    </tr>
  );
}

export default function MessageViewer({ message, isLoading, queueName }: Props) {
  const [activeTab, setActiveTab] = useState<Tab>('text');
  const [showMqmd, setShowMqmd] = useState(true);
  const { isDark } = useThemeStore();

  if (isLoading) {
    return (
      <div className="h-full flex items-center justify-center text-gray-500 dark:text-gray-400">
        <RefreshCw className="w-5 h-5 animate-spin mr-2" />
        Loading message...
      </div>
    );
  }

  if (!message) {
    return (
      <div className="h-full flex flex-col items-center justify-center text-gray-400 border border-gray-200 dark:border-gray-700 rounded-lg">
        <div className="text-4xl mb-3">📩</div>
        <p className="text-sm">Select a message to view its contents</p>
      </div>
    );
  }

  const content = activeTab === 'json' ? (message.jsonView ?? message.textView ?? '')
    : activeTab === 'xml' ? (message.xmlView ?? message.textView ?? '')
    : activeTab === 'hex' ? (message.hexView ?? '')
    : (message.textView ?? '');

  const copyContent = () => {
    navigator.clipboard.writeText(content ?? '').catch(console.error);
  };

  const downloadContent = () => {
    const ext = activeTab === 'json' ? 'json' : activeTab === 'xml' ? 'xml' : activeTab === 'hex' ? 'txt' : 'txt';
    const blob = new Blob([content ?? ''], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${queueName}-${message.messageId.substring(0, 8)}.${ext}`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const tabs: { id: Tab; label: string; available: boolean }[] = [
    { id: 'text', label: 'Text', available: true },
    { id: 'json', label: 'JSON', available: !!message.jsonView },
    { id: 'xml', label: 'XML', available: !!message.xmlView },
    { id: 'hex', label: 'Hex', available: true },
  ];

  const syntaxLang = activeTab === 'json' ? 'json' : activeTab === 'xml' ? 'xml' : activeTab === 'hex' ? 'plaintext' : 'plaintext';

  return (
    <div className="h-full flex flex-col border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
      {/* MQMD header */}
      <div className="flex-shrink-0 border-b border-gray-200 dark:border-gray-700">
        <button
          onClick={() => setShowMqmd(!showMqmd)}
          className="w-full flex items-center gap-2 px-4 py-2.5 text-xs font-semibold text-gray-600 dark:text-gray-300 bg-gray-50 dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
        >
          {showMqmd ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
          Message Descriptor (MQMD)
          <span className="ml-auto font-normal text-gray-400">{message.rawBodySize.toLocaleString()} bytes · {message.contentType}</span>
        </button>
        {showMqmd && (
          <div className="px-4 py-2 bg-gray-50 dark:bg-gray-800 border-t border-gray-100 dark:border-gray-700">
            <table className="w-full">
              <tbody>
                <MqmdRow label="Message ID" value={message.messageId} />
                <MqmdRow label="Correlation ID" value={message.correlationId} />
                <MqmdRow label="Put Timestamp" value={message.putDateTime ? new Date(message.putDateTime).toLocaleString() : undefined} />
                <MqmdRow label="Put Application" value={message.putApplicationName} />
                <MqmdRow label="User ID" value={message.userId} />
                <MqmdRow label="Format" value={message.format} />
                <MqmdRow label="CCSID" value={message.codedCharacterSetId} />
                <MqmdRow label="Encoding" value={message.encoding} />
                <MqmdRow label="Priority" value={message.priority} />
                <MqmdRow label="Persistence" value={message.persistence} />
                <MqmdRow label="Expiry" value={message.expiry === -1 ? 'Unlimited' : message.expiry} />
                <MqmdRow label="Reply-To Queue" value={message.replyToQueue} />
                <MqmdRow label="Reply-To QM" value={message.replyToQueueManager} />
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Tabs */}
      <div className="flex-shrink-0 flex items-center justify-between px-2 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <div className="flex">
          {tabs.map(tab => (
            <button
              key={tab.id}
              disabled={!tab.available}
              onClick={() => setActiveTab(tab.id)}
              className={`px-4 py-2.5 text-xs font-medium border-b-2 transition-colors ${
                activeTab === tab.id
                  ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                  : tab.available
                    ? 'border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
                    : 'border-transparent text-gray-300 dark:text-gray-600 cursor-not-allowed'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-1">
          <button onClick={copyContent} className="p-1.5 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500" title="Copy">
            <Copy className="w-3.5 h-3.5" />
          </button>
          <button onClick={downloadContent} className="p-1.5 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500" title="Download">
            <Download className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto">
        {activeTab === 'hex' ? (
          <pre className="p-4 text-xs font-mono text-gray-700 dark:text-gray-300 whitespace-pre leading-5">
            {message.hexView || '(empty)'}
          </pre>
        ) : content ? (
          <SyntaxHighlighter
            language={syntaxLang}
            style={isDark ? atomOneDark : atomOneLight}
            customStyle={{ margin: 0, borderRadius: 0, fontSize: '12px', height: '100%' }}
            wrapLongLines
          >
            {content}
          </SyntaxHighlighter>
        ) : (
          <div className="p-4 text-sm text-gray-400 italic">(empty body)</div>
        )}
      </div>
    </div>
  );
}
