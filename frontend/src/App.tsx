import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import AppLayout from './components/layout/AppLayout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import ConfigPage from './pages/ConfigPage';
import QueuesPage from './pages/QueuesPage';
import MessageBrowserPage from './pages/MessageBrowserPage';
import CoherencePage from './pages/CoherencePage';
import MskPage from './pages/MskPage';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }>
          <Route index element={<DashboardPage />} />
          <Route path="config" element={<ConfigPage />} />
          <Route path="queues" element={<QueuesPage />} />
          <Route path="queues/:queueName" element={<MessageBrowserPage />} />
          <Route path="coherence" element={<CoherencePage />} />
          <Route path="msk" element={<MskPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
