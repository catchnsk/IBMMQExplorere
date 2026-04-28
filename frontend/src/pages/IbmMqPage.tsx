import { NavLink, Outlet, useMatch } from 'react-router-dom';
import { Settings, Database } from 'lucide-react';
import clsx from 'clsx';

export default function IbmMqPage() {
  const onConfig = useMatch('/ibm-mq') || useMatch('/ibm-mq/');
  const onQueues = useMatch('/ibm-mq/queues') || useMatch('/ibm-mq/queues/*');

  return (
    <div className="h-full flex flex-col">
      <div className="border-b border-gray-200 dark:border-gray-700 flex-shrink-0 mb-4">
        <nav className="flex gap-1 px-1">
          <NavLink
            to="/ibm-mq"
            end
            className={clsx(
              'flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
              onConfig
                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'
            )}
          >
            <Settings className="w-4 h-4" />
            Configuration
          </NavLink>
          <NavLink
            to="/ibm-mq/queues"
            className={clsx(
              'flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
              onQueues
                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'
            )}
          >
            <Database className="w-4 h-4" />
            Queues
          </NavLink>
        </nav>
      </div>
      <div className="flex-1 overflow-auto min-h-0">
        <Outlet />
      </div>
    </div>
  );
}
