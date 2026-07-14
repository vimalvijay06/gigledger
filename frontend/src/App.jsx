import { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { Mic } from 'lucide-react';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import LedgerPage from './pages/LedgerPage';
import CapturePage from './pages/CapturePage';
import AnalyticsPage from './pages/AnalyticsPage';
import ProfilePage from './pages/ProfilePage';
import LogTaskPage from './pages/LogTaskPage';
import LogPayoutPage from './pages/LogPayoutPage';
import PolicyPulsePage from './pages/PolicyPulsePage';
import ComplaintsPage from './pages/ComplaintsPage';
import TopBar from './components/TopBar';
import BottomNav from './components/BottomNav';
import VoiceAssistantModal from './components/VoiceAssistantModal';

/**
 * AppLayout — wrapper layout enforcing mobile app-shell view.
 * Displays header, bottom tab nav, and current route page.
 */
function AppLayout() {
  const [showVoice, setShowVoice] = useState(false);

  return (
    <div id="app" style={{ position: 'relative' }}>
      {/* Top logo & news trigger */}
      <TopBar />

      {/* Main scrolling viewport content */}
      <main id="center" style={{ paddingBottom: '90px' }}>
        <Outlet />
      </main>

      {/* Raised Floating Voice Assistant Button */}
      <button
        id="btn-voice-trigger"
        onClick={() => setShowVoice(true)}
        className="voice-fab"
        title="Voice Assistant"
        style={{ cursor: 'pointer' }}
      >
        <Mic size={24} color="#0f1117" strokeWidth={2.5} />
      </button>

      {/* Primary bottom tab layout */}
      <BottomNav />

      {/* Voice Assistant Overlay Dialog */}
      {showVoice && (
        <VoiceAssistantModal isOpen={showVoice} onClose={() => setShowVoice(false)} />
      )}
    </div>
  );
}

/**
 * Route protection guard checking localStorage authentication status.
 * Re-routes to /login if no valid token exists.
 */
function PrivateRoute({ children }) {
  const token = localStorage.getItem('gl_token');
  return token ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Auth entry route */}
        <Route path="/login" element={
          localStorage.getItem('gl_token') ? <Navigate to="/" replace /> : <LoginPage />
        } />

        {/* Protected App-Shell Routes */}
        <Route path="/" element={
          <PrivateRoute><AppLayout /></PrivateRoute>
        }>
          <Route index element={<DashboardPage />} />
          <Route path="ledger" element={<LedgerPage />} />
          <Route path="capture" element={<CapturePage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="policy-pulse" element={<PolicyPulsePage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="log-task" element={<LogTaskPage />} />
          <Route path="log-payout" element={<LogPayoutPage />} />
          <Route path="complaints" element={<ComplaintsPage />} />
        </Route>

        {/* Catch-all redirect to Dashboard */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
