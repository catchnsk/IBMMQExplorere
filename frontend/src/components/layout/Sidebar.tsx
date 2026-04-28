import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Server, FileSearch, ServerCog, Layers } from 'lucide-react';
import { useConnectionStore } from '../../store/connectionStore';
import clsx from 'clsx';

const navItems = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, exact: true },
  { to: '/ibm-mq', label: 'IBM MQ', icon: Server, exact: false },
  { to: '/coherence', label: 'Coherence', icon: ServerCog, exact: false },
  { to: '/msk', label: 'MSK Kafka', icon: Layers, exact: false },
];

export default function Sidebar() {
  const activeConfig = useConnectionStore(s => s.activeConfig);

  return (
    <aside className="w-56 bg-gray-900 dark:bg-gray-950 text-white flex flex-col flex-shrink-0">
      <div className="p-4 border-b border-gray-700">
        <div className="flex items-center gap-2">
          <FileSearch className="w-6 h-6 text-blue-400" />
          <div>
            <div className="font-semibold text-sm">IBM MQ Explorer</div>
            <div className="text-xs text-gray-400">Web Console</div>
          </div>
        </div>
      </div>

      <nav className="flex-1 p-3 space-y-1">
        {navItems.map(({ to, label, icon: Icon, exact }) => (
          <NavLink
            key={to}
            to={to}
            end={exact}
            className={({ isActive }) =>
              clsx(
                'flex items-center gap-3 px-3 py-2 rounded text-sm transition-colors',
                isActive
                  ? 'bg-blue-600 text-white'
                  : 'text-gray-300 hover:bg-gray-700 hover:text-white'
              )
            }
          >
            <Icon className="w-4 h-4" />
            {label}
          </NavLink>
        ))}
      </nav>

      {activeConfig && (
        <div className="p-3 border-t border-gray-700">
          <div className="text-xs text-gray-400 mb-1">Active Connection</div>
          <div className="text-xs text-green-400 font-medium truncate">{activeConfig.configName}</div>
          <div className="text-xs text-gray-500 truncate">{activeConfig.queueManagerName}</div>
        </div>
      )}
    </aside>
  );
}
