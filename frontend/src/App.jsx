import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import LedgerPage from './pages/LedgerPage';
import CapturePage from './pages/CapturePage';
import AnalyticsPage from './pages/AnalyticsPage';
import ProfilePage from './pages/ProfilePage';
import NotificationsPage from './pages/NotificationsPage';
import LogTaskPage from './pages/LogTaskPage';
import LogPayoutPage from './pages/LogPayoutPage';
import TopBar from './components/TopBar';
import BottomNav from './components/BottomNav';

/**
 * AppLayout — wrapper layout enforcing mobile app-shell view.
 * Displays persistent fixed TopBar & BottomNav around child routes.
 */
function AppLayout() {
  return (
    <div className="app-shell">
      <TopBar />
      <main className="app-content">
        <Outlet />
      </main>
      <BottomNav />
    </div>
  );
}

function PrivateRoute({ children }) {
  const token = localStorage.getItem('gl_token');
  return token ? children : <Navigate to="/login" replace />;
}

function PublicRoute({ children }) {
  const token = localStorage.getItem('gl_token');
  return token ? <Navigate to="/" replace /> : children;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public Routes */}
        <Route path="/login" element={
          <PublicRoute><LoginPage /></PublicRoute>
        } />

        {/* Protected App-Shell Routes */}
        <Route path="/" element={
          <PrivateRoute><AppLayout /></PrivateRoute>
        }>
          <Route index element={<DashboardPage />} />
          <Route path="ledger" element={<LedgerPage />} />
          <Route path="capture" element={<CapturePage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="log-task" element={<LogTaskPage />} />
          <Route path="log-payout" element={<LogPayoutPage />} />
        </Route>

        {/* Catch-all redirect to Dashboard */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
