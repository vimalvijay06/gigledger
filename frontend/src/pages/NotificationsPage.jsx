import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, ArrowLeft } from 'lucide-react';
import api from '../api/client';

export default function NotificationsPage() {
  const navigate = useNavigate();
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/tasks')
      .then(res => {
        // Discrepancy is when a payout is logged and actualAmount < promisedAmount (difference > 0)
        const discrepancies = res.data.filter(t => t.payoutLogged && Number(t.difference) > 0);
        setAlerts(discrepancies);
      })
      .catch(() => setError('Failed to load notifications.'))
      .finally(() => setLoading(false));
  }, []);

  function fmt(n) {
    return '₹' + Number(n).toFixed(2);
  }

  if (loading) {
    return (
      <div className="page" style={{ display: 'flex', justifyContent: 'center', paddingTop: '4rem' }}>
        <span className="spinner" style={{ width: 36, height: 36, borderWidth: 3 }} />
      </div>
    );
  }

  return (
    <div className="page animate-in">
      {/* Header with Back button */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.5rem' }}>
        <button
          className="top-bar-icon-btn"
          onClick={() => navigate('/')}
          style={{ width: 36, height: 36 }}
        >
          <ArrowLeft size={20} />
        </button>
        <div>
          <h1 className="page-title" style={{ margin: 0, fontSize: '1.4rem' }}>Discrepancy Alerts</h1>
        </div>
      </div>

      {error && <div className="alert alert-error">⚠ {error}</div>}

      {alerts.length === 0 ? (
        <div className="card">
          <div className="empty-state" style={{ padding: '3rem 1rem' }}>
            <div style={{
              background: 'rgba(34, 197, 94, 0.08)',
              color: 'var(--color-success)',
              width: 56, height: 56,
              borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              margin: '0 auto 1rem auto'
            }}>
              ✓
            </div>
            <h3>All clear!</h3>
            <p>You have no underpayment alerts. Every logged payout matches or exceeds the promised amount.</p>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', paddingLeft: 'var(--space-2)' }}>
            You have {alerts.length} flagged payout discrepancies:
          </p>
          
          {alerts.map(alert => (
            <div key={alert.id} className="discrepancy-alert-card">
              <AlertTriangle className="discrepancy-alert-icon" size={20} />
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                  <span style={{ fontSize: '0.88rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                    Earnings Discrepancy
                  </span>
                  <span style={{ fontSize: '0.8rem', fontWeight: 800, color: 'var(--color-danger)' }}>
                    -{fmt(alert.difference)}
                  </span>
                </div>
                
                <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.4 }}>
                  For delivery logged on {new Date(alert.acceptedAt).toLocaleString('en-IN', {
                    dateStyle: 'medium', timeStyle: 'short'
                  })}. Promised fare was {fmt(alert.promisedAmount)} but you were only credited {fmt(alert.actualAmount)}.
                </p>

                {alert.deductionReason && (
                  <div style={{
                    marginTop: '8px',
                    padding: '6px 10px',
                    background: 'rgba(255, 255, 255, 0.03)',
                    borderLeft: '2px solid var(--color-border)',
                    fontSize: '0.75rem',
                    color: 'var(--text-secondary)',
                    borderRadius: '0 4px 4px 0'
                  }}>
                    Platform stated reason: <em>"{alert.deductionReason}"</em>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
