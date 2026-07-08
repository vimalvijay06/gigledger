import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import TasksPage from './pages/TasksPage';
import LogTaskPage from './pages/LogTaskPage';
import LogPayoutPage from './pages/LogPayoutPage';
import Navbar from './components/Navbar';

/**
 * App.jsx — Root component.
 *
 * Routing strategy:
 * - /login  → public; redirects to /tasks if already logged in
 * - /tasks  → protected; redirects to /login if no token
 * - /log-task    → protected
 * - /log-payout  → protected
 *
 * PrivateRoute: a tiny wrapper that checks localStorage for a token.
 * This is a UI-level guard only — the backend independently validates
 * the JWT on every API call, so a user can't fake data by bypassing this.
 */

function PrivateRoute({ children }) {
  const token = localStorage.getItem('gl_token');
  return token ? children : <Navigate to="/login" replace />;
}

function PublicRoute({ children }) {
  const token = localStorage.getItem('gl_token');
  return token ? <Navigate to="/tasks" replace /> : children;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={
          <PublicRoute><LoginPage /></PublicRoute>
        } />
        <Route path="/tasks" element={
          <PrivateRoute><><Navbar /><TasksPage /></></PrivateRoute>
        } />
        <Route path="/log-task" element={
          <PrivateRoute><><Navbar /><LogTaskPage /></></PrivateRoute>
        } />
        <Route path="/log-payout" element={
          <PrivateRoute><><Navbar /><LogPayoutPage /></></PrivateRoute>
        } />
        {/* Default redirect */}
        <Route path="*" element={<Navigate to="/tasks" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
