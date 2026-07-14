import { Link, useNavigate } from 'react-router-dom';
import { Newspaper } from 'lucide-react';

/**
 * Top header bar displaying the GigLedger logo and feed navigation actions.
 * Contains no in-app notification bell or panel to rely solely on Email.
 */
export default function TopBar() {
  const navigate = useNavigate();

  return (
    <header className="top-bar">
      <Link to="/" className="top-bar-logo">
        Gig<span className="accent">Ledger</span>
      </Link>

      <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
        <button
          id="btn-policy-pulse"
          className="top-bar-icon-btn"
          onClick={() => navigate('/policy-pulse')}
          title="Policy Pulse Feed"
          style={{ cursor: 'pointer' }}
        >
          <Newspaper size={22} strokeWidth={2} />
        </button>
      </div>
    </header>
  );
}
