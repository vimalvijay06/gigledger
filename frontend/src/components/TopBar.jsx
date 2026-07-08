import { Link, useNavigate } from 'react-router-dom';
import { Bell } from 'lucide-react';
import { useState, useEffect } from 'react';
import api from '../api/client';

export default function TopBar() {
  const navigate = useNavigate();
  const [unreadAlerts, setUnreadAlerts] = useState(0);

  // Poll tasks once on mount to see if there are payouts logged with discrepancy shortfalls
  useEffect(() => {
    api.get('/tasks')
      .then(res => {
        const discrepancies = res.data.filter(t => t.payoutLogged && Number(t.difference) > 0);
        setUnreadAlerts(discrepancies.length);
      })
      .catch(() => {});
  }, []);

  return (
    <header className="top-bar">
      <Link to="/" className="top-bar-logo">
        Gig<span className="accent">Ledger</span>
      </Link>

      <button
        id="btn-notifications"
        className="top-bar-icon-btn"
        onClick={() => navigate('/notifications')}
        style={{ position: 'relative' }}
        title="Discrepancy Alerts"
      >
        <Bell size={22} strokeWidth={2} />
        {unreadAlerts > 0 && (
          <span className="notification-badge">{unreadAlerts}</span>
        )}
      </button>
    </header>
  );
}
