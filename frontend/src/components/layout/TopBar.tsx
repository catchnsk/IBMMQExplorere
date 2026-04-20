import { Moon, Sun, LogOut, User } from 'lucide-react';
import { useThemeStore } from '../../store/themeStore';
import { useAuthStore } from '../../store/authStore';
import { mqApi } from '../../api/mqApi';
import { useNavigate } from 'react-router-dom';

export default function TopBar() {
  const { isDark, toggle } = useThemeStore();
  const { username, isAdmin, clearUser } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await mqApi.logout();
    } finally {
      clearUser();
      navigate('/login');
    }
  };

  return (
    <header className="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-6 py-3 flex items-center justify-between">
      <h1 className="text-lg font-semibold text-gray-800 dark:text-gray-100">
        IBM MQ Explorer
      </h1>
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-300">
          <User className="w-4 h-4" />
          <span>{username}</span>
          {isAdmin() && (
            <span className="bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300 text-xs px-2 py-0.5 rounded">
              ADMIN
            </span>
          )}
        </div>
        <button
          onClick={toggle}
          className="p-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors text-gray-600 dark:text-gray-300"
          title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
        >
          {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
        </button>
        <button
          onClick={handleLogout}
          className="flex items-center gap-2 text-sm text-red-600 dark:text-red-400 hover:text-red-700 transition-colors"
        >
          <LogOut className="w-4 h-4" />
          Logout
        </button>
      </div>
    </header>
  );
}
