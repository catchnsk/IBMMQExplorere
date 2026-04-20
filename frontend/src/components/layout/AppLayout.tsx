import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import { useConnectionStore } from '../../store/connectionStore';

export default function AppLayout() {
  const activeConfig = useConnectionStore(s => s.activeConfig);

  return (
    <div className="flex h-screen bg-gray-50 dark:bg-gray-900 overflow-hidden">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <TopBar />
        {activeConfig && (
          <div className="bg-blue-600 text-white text-xs px-4 py-1.5 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-green-400 inline-block"></span>
            Connected to: <strong>{activeConfig.queueManagerName}</strong>
            on <strong>{activeConfig.host}:{activeConfig.port}</strong>
            via channel <strong>{activeConfig.channel}</strong>
          </div>
        )}
        <main className="flex-1 overflow-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
