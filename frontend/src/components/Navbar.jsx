import { useNavigate, Link } from 'react-router-dom';

/**
 * Navbar — persistent top bar shown on all protected pages.
 *
 * Shows the GigLedger brand, nav links, and a logout button.
 * On logout: clears localStorage tokens and redirects to /login.
 */
export default function Navbar() {
  const navigate = useNavigate();
  const user = JSON.parse(localStorage.getItem('gl_user') || '{}');

  function handleLogout() {
    localStorage.removeItem('gl_token');
    localStorage.removeItem('gl_user');
    navigate('/login');
  }

  return (
    <nav className="navbar">
      <Link to="/tasks" className="navbar-brand">
        Gig<span className="accent">Ledger</span>
      </Link>

      <div className="navbar-actions">
        <Link to="/log-task" className="btn btn-ghost btn--sm">+ Log Task</Link>
        <Link to="/log-payout" className="btn btn-ghost btn--sm">Log Payout</Link>
        <button
          id="btn-logout"
          className="btn btn-ghost btn--sm"
          onClick={handleLogout}
        >
          Logout
        </button>
      </div>
    </nav>
  );
}
